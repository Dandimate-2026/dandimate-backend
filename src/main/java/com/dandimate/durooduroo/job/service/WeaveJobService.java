/**
 * 마감(조기·데모·마감 시각)과 다시 엮기에서 WEAVE_SCROLL 작업을 만들고, Worker가 넘긴 작업을 실행한다.
 * - 방 상태 변경과 작업(QUEUED) 생성은 한 트랜잭션이다.
 * - AI 호출(장면 카드·엮기)은 트랜잭션 밖에서 하고, 생성 결과 저장·SUCCEEDED·GROWING 전환·SCROLL_READY 알림은 한 트랜잭션이다.
 * - WEAVING에 들어가면 미응답 되묻기는 EXPIRED가 된다.
 */
package com.dandimate.durooduroo.job.service;

import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.job.repository.AiJobRepository;
import com.dandimate.durooduroo.askback.domain.Askback;
import com.dandimate.durooduroo.askback.repository.AskbackRepository;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.scroll.service.ScrollGenerationService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WeaveJobService {

    private static final Logger log = LoggerFactory.getLogger(WeaveJobService.class);

    private final RoomRepository rooms;
    private final AiJobRepository jobs;
    private final ScrollGenerationService paragraphs;
    private final AskbackRepository askbacks;
    private final MemberRepository members;
    private final NotificationRepository notifications;
    private final com.dandimate.durooduroo.event.EventLogger events;
    private final TransactionTemplate tx;

    public WeaveJobService(RoomRepository rooms, AiJobRepository jobs, ScrollGenerationService paragraphs,
                           AskbackRepository askbacks, MemberRepository members, NotificationRepository notifications,
                           com.dandimate.durooduroo.event.EventLogger events, PlatformTransactionManager transactionManager) {
        this.rooms = rooms;
        this.jobs = jobs;
        this.paragraphs = paragraphs;
        this.askbacks = askbacks;
        this.members = members;
        this.notifications = notifications;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 조기·데모 마감: CIRCULATING → WEAVING + 작업 생성. 이미 엮는 중이면 진행 중인 작업을 돌려준다. */
    @Transactional
    public AiJob close(String roomId) {
        Room room = lockRoom(roomId);
        if (room.getStatus() == RoomStatus.WEAVING) {
            return activeJob(roomId).orElseThrow(() ->
                    new BusinessException(ErrorCode.ROOM_WEAVING, "이미 마감됐어요. 다시 엮기를 사용해 주세요."));
        }
        return startWeaving(room);
    }

    /** 방장 수동 다시 엮기. 방당 재생성 한도에 포함되며, 진행 중인 작업이 있으면 그것을 돌려준다. */
    @Transactional
    public AiJob reweave(String roomId) {
        Room room = lockRoom(roomId);
        Optional<AiJob> active = activeJob(roomId);
        if (active.isPresent()) return active.get();
        room.useRegeneration();
        return jobs.save(AiJob.queued(roomId, AiJob.WEAVE_SCROLL));
    }

    /**
     * 마감 시각 도달(mode=SCHEDULED). 스케줄러가 호출하며, 잠근 뒤 상태와 시각을 다시 확인한다.
     * CIRCULATING은 엮기를 시작하고, COLLECTING(공개 원문 없음)은 한 번 24시간 연장한 뒤 그래도 없으면 빈 두루마리로 끝낸다.
     */
    @Transactional
    public void closeIfDue(String roomId, LocalDateTime now) {
        Room room = lockRoom(roomId);
        if (room.getDeadlineAt().isAfter(now)) return;
        switch (room.getStatus()) {
            case CIRCULATING -> startWeaving(room);
            case COLLECTING -> {
                if (room.extendDeadlineOnce()) {
                    log.info("Room {} has no entries at deadline; extended once to {}", roomId, room.getDeadlineAt());
                } else {
                    room.completeWithoutScroll();
                }
            }
            default -> { }
        }
    }

    /** Worker가 선점(RUNNING)한 작업을 실행한다. 예외가 나면 저장 트랜잭션은 롤백되고 Worker가 재시도를 결정한다. */
    public void execute(String jobId) {
        String roomId = tx.execute(status -> jobs.findById(jobId).filter(job -> "RUNNING".equals(job.getStatus())).map(AiJob::getRoomId).orElse(null));
        if (roomId == null) return;
        var plan = paragraphs.prepare(roomId);
        var woven = paragraphs.weave(plan);
        tx.executeWithoutResult(status -> {
            AiJob job = jobs.findById(jobId).orElseThrow();
            if (!"RUNNING".equals(job.getStatus())) return;
            var scroll = paragraphs.save(plan, woven);
            job.succeed();
            lockRoom(roomId).completeWeaving();
            members.findAllByRoomId(roomId).forEach(member ->
                    notifications.save(Notification.create(member.getId(), "SCROLL_READY", "SCROLL", scroll.getId())));
        });
    }

    private AiJob startWeaving(Room room) {
        room.startWeaving();
        askbacks.findAllByRoomIdAndStatus(room.getId(), "PENDING").forEach(askback -> {
            askback.expire();
            events.log(room.getId(), askback.getTargetMemberId(), "askback_expired");
        });
        return jobs.save(AiJob.queued(room.getId(), AiJob.WEAVE_SCROLL));
    }

    private Room lockRoom(String roomId) {
        return rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "존재하지 않는 방입니다."));
    }

    private Optional<AiJob> activeJob(String roomId) {
        return jobs.findFirstByRoomIdAndTypeAndStatusInOrderByCreatedAtDesc(roomId, AiJob.WEAVE_SCROLL, AiJob.ACTIVE_STATUSES);
    }
}
