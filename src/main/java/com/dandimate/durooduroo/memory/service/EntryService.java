/**
 * 첫 한마디·덧붙임 확정, 수정·삭제, 패스, 첫 한마디 "나중에"를 처리한다.
 * - 첫 한마디는 사진을 올린 사람만, 사진당 하나. 확정하면 사진이 돌려보기에 노출되고 방이 CIRCULATING이 된다.
 * - 덧붙임은 타인의 노출된 사진에만, 참여자·사진당 2개까지. 덧붙이면 같은 사진의 내 패스는 지워진다.
 */
package com.dandimate.durooduroo.memory.service;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.EntryKind;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.domain.PhotoPass;
import com.dandimate.durooduroo.memory.dto.CreateEntryRequest;
import com.dandimate.durooduroo.memory.dto.EntryView;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.memory.repository.PhotoPassRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.RoomAction;
import com.dandimate.durooduroo.room.service.RoomStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntryService {

    public static final int ADDON_LIMIT_PER_MEMBER = 2;

    private final PhotoRepository photos;
    private final MemberRepository members;
    private final MemoryEntryRepository entries;
    private final PhotoPassRepository passes;
    private final RoomStateService roomStates;
    private final com.dandimate.durooduroo.askback.repository.AskbackRepository askbacks;
    private final TranscriptionService transcriptions;
    private final com.dandimate.durooduroo.storage.MediaUrls mediaUrls;
    private final com.dandimate.durooduroo.askback.service.AskbackAnalysisService askbackAnalysis;
    private final com.dandimate.durooduroo.event.EventLogger events;
    private final com.dandimate.durooduroo.room.service.RoomHomeService roomHome;

    public EntryService(PhotoRepository photos, MemberRepository members, MemoryEntryRepository entries,
                        PhotoPassRepository passes, RoomStateService roomStates,
                        com.dandimate.durooduroo.askback.repository.AskbackRepository askbacks, TranscriptionService transcriptions,
                        com.dandimate.durooduroo.storage.MediaUrls mediaUrls, com.dandimate.durooduroo.askback.service.AskbackAnalysisService askbackAnalysis,
                        com.dandimate.durooduroo.event.EventLogger events, com.dandimate.durooduroo.room.service.RoomHomeService roomHome) {
        this.photos = photos;
        this.members = members;
        this.entries = entries;
        this.passes = passes;
        this.roomStates = roomStates;
        this.askbacks = askbacks;
        this.transcriptions = transcriptions;
        this.mediaUrls = mediaUrls;
        this.askbackAnalysis = askbackAnalysis;
        this.events = events;
        this.roomHome = roomHome;
    }

    /** askbackJobId는 이 확정으로 만들어진(또는 이미 대기 중인) 되묻기 분석 작업. 질문을 만들 수 없는 상태면 null. */
    public record Created(EntryView entry, String askbackJobId) {}

    @Transactional
    public Created create(String photoId, String authorization, CreateEntryRequest request) {
        Photo photo = findPhoto(photoId);
        Member me = requireMember(photo, authorization);
        String transcript = request.transcript().strip();
        if ("VOICE".equals(request.inputMode()) && (request.transcriptionId() == null || request.transcriptionId().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "음성 입력은 transcriptionId가 필요합니다.");
        }
        requireLength(request.kind(), transcript);
        boolean own = me.getId().equals(photo.getMemberId());

        switch (request.kind()) {
            case FIRST -> {
                if (!own) throw new BusinessException(ErrorCode.FORBIDDEN, "첫 한마디는 사진을 올린 사람만 남길 수 있어요.");
                roomStates.require(photo.getRoomId(), RoomAction.UPLOAD_OR_FIRST_ENTRY);
                if (!photo.isReady()) throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "사진 업로드가 끝나지 않았어요.");
                if (entries.existsByPhotoIdAndKind(photoId, EntryKind.FIRST)) {
                    throw new BusinessException(ErrorCode.LIMIT_REACHED, "첫 한마디는 사진당 하나예요. 수정해 주세요.");
                }
            }
            case ADDON -> {
                if (own) throw new BusinessException(ErrorCode.FORBIDDEN, "내 사진에는 덧붙일 수 없어요.");
                requirePublished(photo);
                roomStates.require(photo.getRoomId(), RoomAction.ADDON_OR_PASS);
                if (entries.countByPhotoIdAndMemberIdAndKind(photoId, me.getId(), EntryKind.ADDON) >= ADDON_LIMIT_PER_MEMBER) {
                    throw new BusinessException(ErrorCode.LIMIT_REACHED, "덧붙임은 사진당 2개까지예요.");
                }
            }
            case ASKBACK_ANSWER -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "되묻기 답변은 되묻기 답변 API를 사용해 주세요.");
        }

        String audioLocation = "VOICE".equals(request.inputMode())
                ? transcriptions.consume(request.transcriptionId(), me.getId(), "PHOTO", photoId).getAudioLocation() : null;
        MemoryEntry entry = entries.save(MemoryEntry.confirm(photo.getRoomId(), photoId, me.getId(), request.kind(),
                request.inputMode(), request.transcriptionId(), audioLocation, transcript));
        if (request.kind() == EntryKind.FIRST) {
            photo.publish();
            roomStates.onFirstEntryConfirmed(photo.getRoomId());
        } else {
            passes.findByPhotoIdAndMemberId(photoId, me.getId()).ifPresent(passes::delete);
            events.log(photo.getRoomId(), me.getId(), "addon_submitted", java.util.Map.of("photoId", photoId));
            logIfAllCompleted(photo.getRoomId());
        }
        photo.bumpSourceRevision();
        String askbackJobId = askbackAnalysis.enqueue(photoId);
        return new Created(EntryView.of(entry, me, mediaUrls.audioUrl(entry)), askbackJobId);
    }

    @Transactional
    public EntryView edit(String entryId, String authorization, String transcript) {
        MemoryEntry entry = findOwnEntry(entryId, authorization);
        roomStates.require(entry.getRoomId(), RoomAction.EDIT_ENTRY);
        if (transcript == null || transcript.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "내용은 필수입니다.");
        requireLength(entry.getKind(), transcript.strip());
        entry.edit(transcript.strip());
        sourceChanged(entry.getPhotoId());
        return EntryView.of(entry, members.findById(entry.getMemberId()).orElseThrow(), mediaUrls.audioUrl(entry));
    }

    @Transactional
    public void delete(String entryId, String authorization) {
        MemoryEntry entry = findOwnEntry(entryId, authorization);
        roomStates.require(entry.getRoomId(), RoomAction.DELETE_CONTENT);
        entries.delete(entry);
        sourceChanged(entry.getPhotoId());
    }

    /**
     * 첫 한마디 없이 사진을 돌려보기에 노출한다("한마디 없음", FR-B3). 사진을 올린 사람만 누를 수 있고, 여러 번 눌러도 같다.
     * 노출된 사진에 덧붙임·패스를 할 수 있도록 첫 한마디 확정과 같이 방을 CIRCULATING으로 바꾼다.
     */
    @Transactional
    public Photo skipFirstEntry(String photoId, String authorization) {
        Photo photo = findPhoto(photoId);
        Member me = requireMember(photo, authorization);
        if (!me.getId().equals(photo.getMemberId())) throw new BusinessException(ErrorCode.FORBIDDEN, "첫 한마디는 사진을 올린 사람만 미룰 수 있어요.");
        roomStates.require(photo.getRoomId(), RoomAction.UPLOAD_OR_FIRST_ENTRY);
        if (!photo.isReady()) throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "사진 업로드가 끝나지 않았어요.");
        photo.publish();
        roomStates.onFirstEntryConfirmed(photo.getRoomId());
        return photo;
    }

    @Transactional
    public void pass(String photoId, String authorization) {
        Photo photo = findPhoto(photoId);
        Member me = requireMember(photo, authorization);
        if (me.getId().equals(photo.getMemberId())) throw new BusinessException(ErrorCode.FORBIDDEN, "내 사진은 패스할 수 없어요.");
        requirePublished(photo);
        roomStates.require(photo.getRoomId(), RoomAction.ADDON_OR_PASS);
        if (entries.countByPhotoIdAndMemberIdAndKind(photoId, me.getId(), EntryKind.ADDON) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미 덧붙인 사진은 패스할 수 없어요.");
        }
        if (passes.findByPhotoIdAndMemberId(photoId, me.getId()).isEmpty()) {
            passes.saveAndFlush(PhotoPass.of(photo.getRoomId(), photoId, me.getId()));
            events.log(photo.getRoomId(), me.getId(), "circulation_passed", java.util.Map.of("photoId", photoId));
            logIfAllCompleted(photo.getRoomId());
        }
    }

    @Transactional
    public void cancelPass(String photoId, String authorization) {
        Photo photo = findPhoto(photoId);
        Member me = requireMember(photo, authorization);
        roomStates.require(photo.getRoomId(), RoomAction.ADDON_OR_PASS);
        passes.findByPhotoIdAndMemberId(photoId, me.getId()).ifPresent(passes::delete);
    }

    /** 사진 삭제 시 종속 원문과 패스를 지우고, 그 사진의 미응답 되묻기는 CANCELLED로 닫는다. */
    @Transactional
    public void deleteForPhoto(String photoId) {
        entries.deleteAll(entries.findAllByPhotoId(photoId));
        passes.deleteAll(passes.findAllByPhotoId(photoId));
        askbacks.findAllByPhotoId(photoId).forEach(com.dandimate.durooduroo.askback.domain.Askback::cancel);
    }

    /** 원문이 바뀌면 사진의 revision을 올리고 되묻기를 다시 분석한다. */
    private void sourceChanged(String photoId) {
        photos.findById(photoId).ifPresent(Photo::bumpSourceRevision);
        askbackAnalysis.enqueue(photoId);
    }

    /** 전원 완료를 처음 달성했을 때 한 번만 남긴다. */
    private void logIfAllCompleted(String roomId) {
        entries.flush();
        if (roomHome.allCompleted(roomId)) events.logOnce(roomId, "all_members_completed");
    }

    private Photo findPhoto(String photoId) {
        return photos.findById(photoId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다."));
    }

    private Member requireMember(Photo photo, String authorization) {
        return members.findByRoomIdAndMemberToken(photo.getRoomId(), MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
    }

    /** 아직 돌려보기에 노출되지 않은 타인 사진은 존재 자체를 알리지 않는다. */
    private void requirePublished(Photo photo) {
        if (!photo.isPublished()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다.");
    }

    private MemoryEntry findOwnEntry(String entryId, String authorization) {
        MemoryEntry entry = entries.findById(entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "원문이 없습니다."));
        Member me = members.findByRoomIdAndMemberToken(entry.getRoomId(), MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
        if (!me.getId().equals(entry.getMemberId())) throw new BusinessException(ErrorCode.FORBIDDEN, "본인 원문만 수정·삭제할 수 있어요.");
        return entry;
    }

    private void requireLength(EntryKind kind, String transcript) {
        if (!kind.acceptsLength(transcript)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "%s은(는) %d~%d자여야 해요.".formatted(kind == EntryKind.FIRST ? "첫 한마디" : "덧붙임", kind.minLength(), kind.maxLength()));
        }
    }
}
