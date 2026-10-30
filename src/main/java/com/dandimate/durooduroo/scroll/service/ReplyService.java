/**
 * 답가 확정·삭제 (명세 9장). 답가는 두루마리가 완성된(GROWING) 방에서만 남길 수 있다.
 * - 확정하면 PENDING 답가와 REPLY_REWEAVE 작업을 만든다. 답가 재엮기는 방당 재생성 한도(10회)에 포함된다.
 * - 본문에 반영된 답가를 지우면 문단 복구 작업을 만든다. 반영 전이거나 반응·거절이면 원래 작업을 돌려준다.
 */
package com.dandimate.durooduroo.scroll.service;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.job.repository.AiJobRepository;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.service.TranscriptionService;
import com.dandimate.durooduroo.room.domain.RoomAction;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.room.service.RoomStateService;
import com.dandimate.durooduroo.scroll.domain.Paragraph;
import com.dandimate.durooduroo.scroll.domain.Reply;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ReplyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplyService {

    private final ParagraphRepository paragraphs;
    private final ReplyRepository replies;
    private final MemberRepository members;
    private final RoomRepository rooms;
    private final RoomStateService roomStates;
    private final TranscriptionService transcriptions;
    private final AiJobRepository jobs;
    private final com.dandimate.durooduroo.scroll.repository.ScrollRepository scrolls;

    public ReplyService(ParagraphRepository paragraphs, ReplyRepository replies, MemberRepository members, RoomRepository rooms,
                        RoomStateService roomStates, TranscriptionService transcriptions, AiJobRepository jobs,
                        com.dandimate.durooduroo.scroll.repository.ScrollRepository scrolls) {
        this.paragraphs = paragraphs;
        this.replies = replies;
        this.members = members;
        this.rooms = rooms;
        this.roomStates = roomStates;
        this.transcriptions = transcriptions;
        this.jobs = jobs;
        this.scrolls = scrolls;
    }

    public record Accepted(Reply reply, AiJob job) {}

    @Transactional
    public Accepted create(String paragraphId, String authorization, String inputMode, String transcriptionId, String transcript) {
        Paragraph paragraph = paragraphs.findById(paragraphId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "문단이 없습니다."));
        Member me = members.findByRoomIdAndMemberToken(paragraph.getRoomId(), MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 답가를 남길 수 있습니다."));
        roomStates.require(paragraph.getRoomId(), RoomAction.REPLY);
        if (!"VOICE".equals(inputMode) && !"TEXT".equals(inputMode)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "inputMode는 VOICE 또는 TEXT여야 합니다.");
        String text = transcript == null ? "" : transcript.strip();
        if (text.isEmpty() || text.length() > 200) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "답가는 1~200자여야 해요.");
        String audioLocation = null;
        if ("VOICE".equals(inputMode)) {
            if (transcriptionId == null || transcriptionId.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "음성 입력은 transcriptionId가 필요합니다.");
            audioLocation = transcriptions.consume(transcriptionId, me.getId(), "PARAGRAPH", paragraphId).getAudioLocation();
        }
        rooms.findByIdForUpdate(paragraph.getRoomId()).orElseThrow().useRegeneration();
        Reply reply = replies.save(Reply.create(paragraph.getRoomId(), paragraphId, paragraph.getPhotoId(), me.getId(), inputMode, transcriptionId, audioLocation, text));
        AiJob job = jobs.save(AiJob.queued(paragraph.getRoomId(), AiJob.REPLY_REWEAVE, reply.getId()));
        return new Accepted(reply, job);
    }

    @Transactional
    public Accepted delete(String replyId, String authorization) {
        Reply reply = replies.findById(replyId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "답가가 없습니다."));
        Member me = members.findByRoomIdAndMemberToken(reply.getRoomId(), MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
        if (!me.getId().equals(reply.getMemberId())) throw new BusinessException(ErrorCode.FORBIDDEN, "본인 답가만 삭제할 수 있습니다.");
        roomStates.require(reply.getRoomId(), RoomAction.DELETE_CONTENT);
        boolean wasApplied = Reply.APPLIED.equals(reply.getStatus());
        reply.delete();
        AiJob job = wasApplied
                ? jobs.save(AiJob.queued(reply.getRoomId(), AiJob.REPLY_REWEAVE, reply.getParagraphId(), "REPLY_DELETED"))
                : jobs.findFirstByTypeAndTargetIdOrderByCreatedAtDesc(AiJob.REPLY_REWEAVE, replyId).orElseThrow();
        return new Accepted(reply, job);
    }

    /** 원문이 지워진 사진의 현재 두루마리 문단을 복구한다 (GROWING에서 원문 삭제). */
    @Transactional
    public void rebuildForPhoto(String roomId, String photoId) {
        scrolls.findTopByRoomIdOrderByVersionDesc(roomId).ifPresent(scroll -> paragraphs.findAllByScrollId(scroll.getId()).stream()
                .filter(p -> photoId.equals(p.getPhotoId()))
                .forEach(p -> jobs.save(AiJob.queued(roomId, AiJob.REPLY_REWEAVE, p.getId(), "ENTRY_DELETED"))));
    }
}
