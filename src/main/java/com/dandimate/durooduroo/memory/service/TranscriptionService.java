/**
 * 음성 받아쓰기 초안을 만들고, 원문 확정 때 소비한다 (명세 2장 처리 흐름).
 * 업로드 → 크기·형식·길이(서버 측정) 검증 → 저장소 보관 → TranscriptionAiService → 초안 저장 → 사용자가 고쳐서 확정.
 * 길이: 첫 한마디 5~60초, 덧붙임·되묻기 답변·답가 1~30초. 파일은 10MB 이하.
 */
package com.dandimate.durooduroo.memory.service;

import com.dandimate.durooduroo.ai.common.AiFailure;
import com.dandimate.durooduroo.ai.transcription.TranscriptionAiService;
import com.dandimate.durooduroo.ai.transcription.TranscriptionCommand;
import com.dandimate.durooduroo.ai.transcription.TranscriptionResult;
import com.dandimate.durooduroo.askback.domain.Askback;
import com.dandimate.durooduroo.askback.repository.AskbackRepository;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.audio.AudioDurationProbe;
import com.dandimate.durooduroo.memory.domain.TranscriptionDraft;
import com.dandimate.durooduroo.memory.repository.TranscriptionDraftRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.RoomAction;
import com.dandimate.durooduroo.room.service.RoomStateService;
import com.dandimate.durooduroo.scroll.domain.Paragraph;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.storage.ObjectStorage;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranscriptionService {

    public static final long MAX_AUDIO_BYTES = 10_000_000;
    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("audio/mp4", ".m4a"), Map.entry("audio/m4a", ".m4a"), Map.entry("audio/x-m4a", ".m4a"), Map.entry("video/mp4", ".mp4"),
            Map.entry("audio/aac", ".aac"), Map.entry("audio/webm", ".webm"), Map.entry("video/webm", ".webm"), Map.entry("audio/ogg", ".ogg"),
            Map.entry("audio/wav", ".wav"), Map.entry("audio/x-wav", ".wav"), Map.entry("audio/wave", ".wav"), Map.entry("audio/mpeg", ".mp3"));

    public record DraftView(String transcriptionId, String audioAssetId, String transcript, List<String> detectedLanguages, LocalDateTime expiresAt) {
    }

    private final TranscriptionDraftRepository drafts;
    private final PhotoRepository photos;
    private final AskbackRepository askbacks;
    private final ParagraphRepository paragraphs;
    private final MemberRepository members;
    private final RoomStateService roomStates;
    private final ObjectStorage storage;
    private final AudioDurationProbe durationProbe;
    private final TranscriptionAiService stt;

    public TranscriptionService(TranscriptionDraftRepository drafts, PhotoRepository photos, AskbackRepository askbacks, ParagraphRepository paragraphs,
                                MemberRepository members, RoomStateService roomStates, ObjectStorage storage, AudioDurationProbe durationProbe,
                                TranscriptionAiService stt) {
        this.drafts = drafts;
        this.photos = photos;
        this.askbacks = askbacks;
        this.paragraphs = paragraphs;
        this.members = members;
        this.roomStates = roomStates;
        this.storage = storage;
        this.durationProbe = durationProbe;
        this.stt = stt;
    }

    /** kind: FIRST(사진을 올린 사람) 또는 ADDON(돌려보기에 노출된 타인 사진). */
    public DraftView forPhoto(String photoId, String authorization, String kind, MultipartFile file) {
        Photo photo = photos.findById(photoId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다."));
        Member me = member(photo.getRoomId(), authorization);
        boolean own = me.getId().equals(photo.getMemberId());
        if ("FIRST".equals(kind)) {
            if (!own) throw new BusinessException(ErrorCode.FORBIDDEN, "첫 한마디는 사진을 올린 사람만 남길 수 있어요.");
            roomStates.require(photo.getRoomId(), RoomAction.UPLOAD_OR_FIRST_ENTRY);
        } else if ("ADDON".equals(kind)) {
            if (own) throw new BusinessException(ErrorCode.FORBIDDEN, "내 사진에는 덧붙일 수 없어요.");
            if (!photo.isPublished()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다.");
            roomStates.require(photo.getRoomId(), RoomAction.ADDON_OR_PASS);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "kind는 FIRST 또는 ADDON이어야 합니다.");
        }
        return create(photo.getRoomId(), me, "PHOTO", photoId, kind, file);
    }

    /** 되묻기 대상자만, 아직 답하지 않은 질문에만. */
    public DraftView forAskback(String askbackId, String authorization, MultipartFile file) {
        Askback askback = askbacks.findById(askbackId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "질문이 없습니다."));
        Member me = member(askback.getRoomId(), authorization);
        if (!me.getId().equals(askback.getTargetMemberId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "질문이 없습니다.");
        if (!Askback.PENDING.equals(askback.getStatus())) throw new BusinessException(ErrorCode.ASKBACK_CLOSED, "이미 끝난 질문이에요.");
        roomStates.require(askback.getRoomId(), RoomAction.ASKBACK_ANSWER);
        return create(askback.getRoomId(), me, "ASKBACK", askbackId, "ASKBACK_ANSWER", file);
    }

    /** 답가 음성. 두루마리가 완성된(GROWING) 방에서만. */
    public DraftView forParagraph(String paragraphId, String authorization, MultipartFile file) {
        Paragraph paragraph = paragraphs.findById(paragraphId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "문단이 없습니다."));
        Member me = member(paragraph.getRoomId(), authorization);
        roomStates.require(paragraph.getRoomId(), RoomAction.REPLY);
        return create(paragraph.getRoomId(), me, "PARAGRAPH", paragraphId, "REPLY", file);
    }

    /** 원문 확정 때 초안을 소비하고 음성 위치를 돌려준다. */
    @Transactional
    public TranscriptionDraft consume(String transcriptionId, String memberId, String contextType, String contextId) {
        TranscriptionDraft draft = drafts.findById(transcriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "받아쓰기 초안을 찾을 수 없어요. 다시 녹음해 주세요."));
        draft.consume(memberId, contextType, contextId);
        return draft;
    }

    /** 확정되지 않고 만료된 초안의 음성은 지운다. */
    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void deleteExpired() {
        for (TranscriptionDraft draft : drafts.findAllByExpiresAtBeforeAndConsumedAtIsNull(LocalDateTime.now())) {
            storage.delete(draft.getAudioLocation());
            drafts.delete(draft);
        }
    }

    private DraftView create(String roomId, Member me, String contextType, String contextId, String kind, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "음성 파일이 비어 있어요.");
        if (file.getSize() > MAX_AUDIO_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "음성 파일은 10MB 이하이어야 해요.");
        String mime = file.getContentType() == null ? "" : file.getContentType().split(";")[0].trim().toLowerCase();
        String extension = EXTENSIONS.get(mime);
        if (extension == null) throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "m4a, mp4, webm, wav 같은 음성 파일만 올릴 수 있어요.");
        byte[] audio;
        try {
            audio = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "음성 파일을 읽지 못했어요.");
        }

        Long durationMs = durationProbe.probe(audio).map(duration -> {
            requireDuration(kind, duration);
            return duration.toMillis();
        }).orElse(null);
        if (durationMs == null) log.warn("Could not measure audio duration for a {} draft (mime {}); skipped length check", kind, mime);

        String draftId = UUID.randomUUID().toString();
        String key = roomId + "/audio/" + draftId + extension;
        storage.putBytes(key, audio, mime);
        String location = storage.locationFor(key);
        TranscriptionResult result;
        try {
            List<String> nicknames = members.findAllByRoomId(roomId).stream().map(Member::getNickname).toList();
            result = stt.transcribe(new TranscriptionCommand(draftId, audio, "audio" + extension, mime, List.of("ko"), nicknames));
        } catch (AiFailure e) {
            storage.delete(location);
            throw toBusiness(e);
        }
        TranscriptionDraft draft = drafts.save(TranscriptionDraft.create(draftId, roomId, me.getId(), contextType, contextId, kind,
                UUID.randomUUID().toString(), location, result.text(), durationMs, result.model(), result.openaiRequestId()));
        return new DraftView(draft.getId(), draft.getAudioAssetId(), draft.getTranscript(), result.languages(), draft.getExpiresAt());
    }

    private void requireDuration(String kind, Duration duration) {
        boolean first = "FIRST".equals(kind);
        Duration min = Duration.ofSeconds(first ? 5 : 1);
        Duration max = Duration.ofSeconds(first ? 60 : 30);
        if (duration.compareTo(min) < 0) throw new BusinessException(ErrorCode.MIN_DURATION_NOT_MET, "음성이 너무 짧아요. " + min.toSeconds() + "초 이상 말해 주세요.");
        if (duration.compareTo(max) > 0) throw new BusinessException(ErrorCode.MAX_DURATION_EXCEEDED, "음성이 너무 길어요. " + max.toSeconds() + "초 안으로 말해 주세요.");
    }

    private BusinessException toBusiness(AiFailure e) {
        return switch (e.getCode()) {
            case NOT_CONFIGURED, OPENAI_RATE_LIMITED -> new BusinessException(ErrorCode.AI_TEMPORARILY_UNAVAILABLE, "받아쓰기를 잠시 쓸 수 없어요. 글로 남겨 주세요.");
            case OPENAI_TIMEOUT -> new BusinessException(ErrorCode.AI_TIMEOUT, "받아쓰기가 오래 걸려요. 글로 남겨 주세요.");
            default -> new BusinessException(ErrorCode.STT_FAILED, "음성을 글로 바꾸지 못했어요. 글로 남겨 주세요.");
        };
    }

    private Member member(String roomId, String authorization) {
        return members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
    }
}
