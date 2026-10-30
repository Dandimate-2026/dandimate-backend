/**
 * 되묻기 조회·답변·기억 안 나요·나중에와 데모 즉시 실행을 처리한다 (명세 6-1장).
 * - 질문은 대상자 본인에게만 보인다. 다른 참여자에게는 존재 여부도 알리지 않는다(404).
 * - 답변은 해당 사진의 ASKBACK_ANSWER 원문으로 저장되어 돌려보기·두루마리 재료가 된다.
 */
package com.dandimate.durooduroo.askback.service;

import com.dandimate.durooduroo.ai.domain.AiJob;
import com.dandimate.durooduroo.ai.repository.AiJobRepository;
import com.dandimate.durooduroo.askback.domain.Askback;
import com.dandimate.durooduroo.askback.dto.AskbackAnswerRequest;
import com.dandimate.durooduroo.askback.dto.AskbackView;
import com.dandimate.durooduroo.askback.repository.AskbackRepository;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.EntryKind;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.dto.EntryView;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.dto.PhotoView;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.photo.service.PhotoUrls;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomAction;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.room.service.RoomStateService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AskbackService {

    /** FR-D3: 참여자당 방 전체 누적 수신 한도. */
    public static final int RECEIVE_LIMIT_PER_MEMBER = 2;

    private final AskbackRepository askbacks;
    private final MemberRepository members;
    private final PhotoRepository photos;
    private final MemoryEntryRepository entries;
    private final RoomRepository rooms;
    private final RoomStateService roomStates;
    private final AskbackRuleService rules;
    private final AiJobRepository jobs;
    private final NotificationRepository notifications;
    private final PhotoUrls photoUrls;

    public AskbackService(AskbackRepository askbacks, MemberRepository members, PhotoRepository photos, MemoryEntryRepository entries,
                          RoomRepository rooms, RoomStateService roomStates, AskbackRuleService rules, AiJobRepository jobs,
                          NotificationRepository notifications, PhotoUrls photoUrls) {
        this.askbacks = askbacks;
        this.members = members;
        this.photos = photos;
        this.entries = entries;
        this.rooms = rooms;
        this.roomStates = roomStates;
        this.rules = rules;
        this.jobs = jobs;
        this.notifications = notifications;
        this.photoUrls = photoUrls;
    }

    /** 내가 받은 질문. 미룬 적 없는 질문을 먼저, 그 안에서는 오래된 순서. */
    @Transactional(readOnly = true)
    public List<AskbackView> mine(String roomId, String authorization, String status) {
        Member me = requireMember(roomId, authorization);
        return askbacks.findAllByRoomIdAndTargetMemberId(roomId, me.getId()).stream()
                .filter(a -> status == null || status.equals(a.getStatus()))
                .sorted(Comparator.comparing((Askback a) -> a.getPostponedAt() != null).thenComparing(Askback::getCreatedAt))
                .map(a -> view(a, false))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public AskbackView detail(String askbackId, String authorization) {
        return view(requireOwn(askbackId, authorization), true);
    }

    @Transactional
    public Map<String, Object> answer(String askbackId, String authorization, AskbackAnswerRequest request) {
        Askback askback = requireOwn(askbackId, authorization);
        roomStates.require(askback.getRoomId(), RoomAction.ASKBACK_ANSWER);
        if ("VOICE".equals(request.inputMode()) && (request.transcriptionId() == null || request.transcriptionId().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "음성 입력은 transcriptionId가 필요합니다.");
        }
        String transcript = request.transcript().strip();
        if (!EntryKind.ASKBACK_ANSWER.acceptsLength(transcript)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "답변은 1~200자여야 해요.");
        if (photos.findById(askback.getPhotoId()).isEmpty()) throw new BusinessException(ErrorCode.ASKBACK_CLOSED, "질문한 사진이 삭제됐어요.");

        MemoryEntry entry = entries.save(MemoryEntry.confirm(askback.getRoomId(), askback.getPhotoId(), askback.getTargetMemberId(),
                EntryKind.ASKBACK_ANSWER, request.inputMode(), request.transcriptionId(), transcript));
        askback.answer(entry.getId());
        Member me = members.findById(askback.getTargetMemberId()).orElseThrow();
        return Map.of("askback", Map.of("id", askback.getId(), "status", askback.getStatus()), "entry", EntryView.of(entry, me));
    }

    @Transactional
    public Askback forgot(String askbackId, String authorization) {
        Askback askback = requireOwn(askbackId, authorization);
        roomStates.require(askback.getRoomId(), RoomAction.ASKBACK_ANSWER);
        askback.forgot();
        return askback;
    }

    @Transactional
    public Askback postpone(String askbackId, String authorization) {
        Askback askback = requireOwn(askbackId, authorization);
        roomStates.require(askback.getRoomId(), RoomAction.ASKBACK_ANSWER);
        askback.postpone();
        return askback;
    }

    /**
     * 데모: 방장이 되묻기 분석을 즉시 실행한다(FR-H9). AskbackAiService를 붙이기 전까지 규칙으로 질문을 만든다.
     * 노출된 사진마다 사진을 올린 사람을 대상으로 하며, FORGOT 응답자·미응답 질문이 있는 대상·수신 한도(2개) 도달자는 건너뛴다.
     */
    @Transactional
    public AiJob runDemo(String roomId, String authorization) {
        Member me = requireMember(roomId, authorization);
        if (me.getRole() != MemberRole.HOST) throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 실행할 수 있습니다.");
        Room room = roomStates.require(roomId, RoomAction.ASKBACK_ANSWER);
        Map<String, List<MemoryEntry>> entriesByPhoto = entries.findAllByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .collect(Collectors.groupingBy(MemoryEntry::getPhotoId));
        for (Photo photo : photos.findAllByRoomId(roomId).stream().filter(Photo::isPublished).sorted(Comparator.comparing(Photo::getPublishedAt)).toList()) {
            List<MemoryEntry> photoEntries = entriesByPhoto.getOrDefault(photo.getId(), List.of());
            String target = photo.getMemberId();
            boolean blocked = askbacks.findAllByPhotoId(photo.getId()).stream()
                    .anyMatch(a -> a.getTargetMemberId().equals(target) && (a.getStatus().equals(Askback.PENDING) || a.getStatus().equals("FORGOT")));
            if (photoEntries.isEmpty() || blocked || askbacks.countByRoomIdAndTargetMemberId(roomId, target) >= RECEIVE_LIMIT_PER_MEMBER) continue;
            var rule = rules.decide(photoEntries);
            Askback askback = askbacks.save(Askback.create(roomId, photo.getId(), target, rule.gapType(), rule.question(),
                    photoEntries.stream().map(MemoryEntry::getId).toList(), room.getDeadlineAt()));
            notifications.save(Notification.create(target, "ASKBACK_RECEIVED", "ASKBACK", askback.getId()));
        }
        AiJob job = AiJob.queued(roomId, AiJob.ASKBACK_ANALYZE);
        job.claim();
        job.succeed();
        return jobs.save(job);
    }

    /** 사진이 삭제된 질문은 목록에서 뺀다(null). */
    private AskbackView view(Askback askback, boolean requirePhoto) {
        Photo photo = photos.findById(askback.getPhotoId()).orElse(null);
        if (photo == null) {
            if (requirePhoto) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "질문한 사진이 삭제됐어요.");
            return null;
        }
        Map<String, Member> roomMembers = members.findAllByRoomId(askback.getRoomId()).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        List<MemoryEntry> photoEntries = entries.findAllByPhotoId(photo.getId());
        List<String> evidenceNicknames = photoEntries.stream()
                .filter(e -> askback.getEvidenceEntryIds().contains(e.getId()))
                .map(e -> roomMembers.get(e.getMemberId()).getNickname())
                .distinct().toList();
        EntryView first = photoEntries.stream().filter(e -> e.getKind() == EntryKind.FIRST).findFirst()
                .map(e -> EntryView.of(e, roomMembers.get(e.getMemberId()))).orElse(null);
        return new AskbackView(askback.getId(), PhotoView.of(photo, roomMembers.get(photo.getMemberId()), photoUrls.imageUrl(photo)),
                askback.getGapType(), askback.getQuestion(), evidenceNicknames, askback.getStatus(), first,
                askback.getCreatedAt(), askback.getExpiresAt());
    }

    private Member requireMember(String roomId, String authorization) {
        rooms.findById(roomId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "존재하지 않는 방입니다."));
        return members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
    }

    /** 대상자가 아니면 질문 존재 자체를 알리지 않도록 404로 응답한다. */
    private Askback requireOwn(String askbackId, String authorization) {
        Askback askback = askbacks.findById(askbackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "질문이 없습니다."));
        Member me = members.findByRoomIdAndMemberToken(askback.getRoomId(), MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
        if (!me.getId().equals(askback.getTargetMemberId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "질문이 없습니다.");
        return askback;
    }
}
