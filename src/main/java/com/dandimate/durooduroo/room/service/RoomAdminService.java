/**
 * 방장 전용 방 관리 (명세 1장 방 관리): 이름·모임 날짜·마감 수정, 방과 모든 데이터 삭제.
 * 삭제는 DB 데이터를 한 트랜잭션에서 지우고, 저장소 파일은 MEDIA_DELETE 작업이 roomId/ 아래를 지운다.
 */
package com.dandimate.durooduroo.room.service;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.job.repository.AiJobRepository;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomAdminService {

    /** roomId 컬럼으로 지우는 엔티티. */
    private static final List<String> ROOM_SCOPED = List.of("VoiceUsage", "MemoryEntry", "PhotoPass", "TranscriptionDraft", "Askback", "Reply",
            "Paragraph", "Scroll", "Photo", "AiJob", "EventLog");
    /** 문단에 딸린 엔티티 (paragraphId로 지운다, Paragraph보다 먼저). */
    private static final List<String> PARAGRAPH_SCOPED = List.of("ParagraphSource", "ParagraphVersion", "ParagraphReaction", "ParagraphRating");

    private final com.dandimate.durooduroo.payment.PaymentOrderRepository paymentOrders;
    private final RoomRepository rooms;
    private final MemberRepository members;
    private final AiJobRepository jobs;
    private final EntityManager em;
    private final com.dandimate.durooduroo.room.service.RoomDetailsService details;
    private final com.dandimate.durooduroo.notification.service.RoomEvents events;

    public RoomAdminService(RoomRepository rooms, MemberRepository members, AiJobRepository jobs, EntityManager em, com.dandimate.durooduroo.payment.PaymentOrderRepository paymentOrders, com.dandimate.durooduroo.notification.service.RoomEvents events, com.dandimate.durooduroo.room.service.RoomDetailsService details) {
        this.details = details;
        this.events = events;
        this.paymentOrders = paymentOrders;
        this.rooms = rooms;
        this.members = members;
        this.jobs = jobs;
        this.em = em;
    }

    /** name(1~20자)·meetingDate·deadlineAt 중 보낸 값만 바꾼다. */
    @Transactional
    public Room update(String roomId, String authorization, String name, LocalDate meetingDate, OffsetDateTime deadlineAt) {
        return update(roomId, authorization, name, meetingDate, deadlineAt, false, null, false, null, null, null, false);
    }

    @Transactional
    public Room update(String roomId, String authorization, String name, LocalDate meetingDate, OffsetDateTime deadlineAt,
            boolean placePresent, com.dandimate.durooduroo.room.dto.RoomPlace place,
            boolean coverPresent, com.dandimate.durooduroo.room.dto.CoverPhotoRef cover) {
        return update(roomId, authorization, name, meetingDate, deadlineAt, placePresent, place, coverPresent, cover, null, null, false);
    }

    /** 기존 정원 변경 호출부 호환용. */
    @Transactional
    public Room update(String roomId, String authorization, String name, LocalDate meetingDate, OffsetDateTime deadlineAt,
            boolean placePresent, com.dandimate.durooduroo.room.dto.RoomPlace place,
            boolean coverPresent, com.dandimate.durooduroo.room.dto.CoverPhotoRef cover, Integer capacity) {
        return update(roomId, authorization, name, meetingDate, deadlineAt, placePresent, place, coverPresent, cover, capacity, null, false);
    }

    @Transactional
    public Room update(String roomId, String authorization, String name, LocalDate meetingDate, OffsetDateTime deadlineAt,
            boolean placePresent, com.dandimate.durooduroo.room.dto.RoomPlace place,
            boolean coverPresent, com.dandimate.durooduroo.room.dto.CoverPhotoRef cover, Integer capacity,
            String scrollTemplateId, boolean scrollTemplatePresent) {
        Room room = requireHostRoom(roomId, authorization);
        if (capacity != null) room.changeCapacity(capacity, members.countByRoomIdAndLeftAtIsNull(roomId));
        if (coverPresent) {
            details.validateCover(cover, com.dandimate.durooduroo.common.auth.CurrentUser.idOrNull());
            room.changeCoverPhoto(cover==null?null:cover.id());
        }
        if (placePresent) room.changePlace(place);
        if (scrollTemplatePresent) room.changeScrollTemplate(scrollTemplateId);
        if (name != null) {
            String trimmed = name.strip();
            if (trimmed.isEmpty() || trimmed.length() > 20) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "방 이름은 1~20자여야 해요.");
            room.rename(trimmed);
        }
        if (meetingDate != null) room.changeMeetingDate(meetingDate);
        if (deadlineAt != null) {
            room.changeDeadline(deadlineAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(), LocalDateTime.now());
        }
        if (name != null || meetingDate != null || deadlineAt != null || placePresent || coverPresent || capacity != null || scrollTemplatePresent) {
            events.toRoom(roomId, "room.updated", java.util.Map.of("roomId", roomId));
        }
        if (scrollTemplatePresent) events.toRoom(roomId, "scroll.template.updated", java.util.Map.of("roomId", roomId, "scrollTemplateId", room.getScrollTemplateId()));
        return room;
    }

    @Transactional
    public void delete(String roomId, String authorization) {
        requireHostRoom(roomId, authorization);
        com.dandimate.durooduroo.payment.PaymentGuard.requireIdle(paymentOrders, roomId);
        deleteData(roomId);
    }

    /** 보존 기간 만료 방을 시스템이 삭제한다. 호출 전에 대상과 시각을 다시 확인해야 한다. */
    @Transactional
    public void deleteExpired(String roomId, LocalDateTime now) {
        Room room = rooms.findByIdForUpdate(roomId).orElse(null);
        if (room == null || room.getDeleteAt().isAfter(now)) return;
        deleteData(roomId);
    }

    /** 심사용 자동 생성 방만 다시 만들 때 사용한다. 외부 API에는 노출하지 않는다. */
    @Transactional
    public void deleteReviewerDemoRoom(String roomId) {
        rooms.findByIdForUpdate(roomId).ifPresent(room -> {
            events.toRoom(roomId,"room.deleted",java.util.Map.of("roomId",roomId));
            for(var member:members.findAllByRoomId(roomId)) events.toMember(roomId,member.getId(),"member.left",java.util.Map.of("memberId",member.getId(),"roomId",roomId));
            deleteData(roomId);
            jobs.save(AiJob.queued(roomId, AiJob.MEDIA_DELETE, "reviewer-demo/" + roomId + "/"));
        });
    }

    private void deleteData(String roomId) {
        for (String entity : PARAGRAPH_SCOPED) {
            em.createQuery("delete from " + entity + " x where x.paragraphId in (select p.id from Paragraph p where p.roomId = :roomId)")
                    .setParameter("roomId", roomId).executeUpdate();
        }
        em.createQuery("delete from Notification n where n.memberId in (select m.id from Member m where m.roomId = :roomId)")
                .setParameter("roomId", roomId).executeUpdate();
        for (String entity : ROOM_SCOPED) {
            em.createQuery("delete from " + entity + " x where x.roomId = :roomId").setParameter("roomId", roomId).executeUpdate();
        }
        em.createQuery("delete from Member m where m.roomId = :roomId").setParameter("roomId", roomId).executeUpdate();
        em.createQuery("delete from Room r where r.id = :roomId").setParameter("roomId", roomId).executeUpdate();
        jobs.save(AiJob.queued(roomId, AiJob.MEDIA_DELETE, roomId + "/"));
    }

    private Room requireHostRoom(String roomId, String authorization) {
        Room room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "존재하지 않는 방입니다."));
        var me = members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
        if (me.getRole() != MemberRole.HOST) throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 할 수 있어요.");
        return room;
    }
}
