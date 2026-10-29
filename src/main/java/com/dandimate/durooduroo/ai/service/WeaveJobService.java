/**
 * 마감·다시 엮기 시 WEAVE_SCROLL 작업을 만들고 실행한다.
 * 방 상태 변경과 작업 생성은 한 트랜잭션, 생성 결과와 SUCCEEDED·GROWING 전환도 한 트랜잭션이다.
 * MVP에서는 요청 스레드에서 바로 실행하며, 비동기 Worker로 바꿀 때 run()만 Worker로 옮긴다.
 */
package com.dandimate.durooduroo.ai.service;

import com.dandimate.durooduroo.ai.domain.AiJob;
import com.dandimate.durooduroo.ai.repository.AiJobRepository;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WeaveJobService {

    private static final Logger log = LoggerFactory.getLogger(WeaveJobService.class);

    private final RoomRepository rooms;
    private final AiJobRepository jobs;
    private final AiParagraphService paragraphs;
    private final TransactionTemplate tx;

    public WeaveJobService(RoomRepository rooms, AiJobRepository jobs, AiParagraphService paragraphs,
                           PlatformTransactionManager transactionManager) {
        this.rooms = rooms;
        this.jobs = jobs;
        this.paragraphs = paragraphs;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** CIRCULATING → WEAVING 전환과 작업 생성. 이미 엮는 중이면 진행 중인 작업을 그대로 돌려준다. */
    public AiJob close(String roomId) {
        AiJob job = tx.execute(status -> {
            Room room = lockRoom(roomId);
            if (room.getStatus() == RoomStatus.WEAVING) {
                return activeJob(roomId).orElseThrow(() ->
                        new BusinessException(ErrorCode.ROOM_WEAVING, "이미 마감됐어요. 다시 엮기를 사용해 주세요."));
            }
            room.startWeaving();
            return jobs.save(AiJob.queued(roomId, AiJob.WEAVE_SCROLL));
        });
        return run(job);
    }

    /** 방장 수동 다시 엮기. 방당 재생성 한도에 포함된다. */
    public AiJob reweave(String roomId) {
        AiJob job = tx.execute(status -> {
            Room room = lockRoom(roomId);
            Optional<AiJob> active = activeJob(roomId);
            if (active.isPresent()) return active.get();
            room.useRegeneration();
            return jobs.save(AiJob.queued(roomId, AiJob.WEAVE_SCROLL));
        });
        return run(job);
    }

    private AiJob run(AiJob queued) {
        if (!"QUEUED".equals(queued.getStatus())) return queued;
        tx.executeWithoutResult(status -> {
            AiJob job = jobs.findById(queued.getId()).orElseThrow();
            job.start();
        });
        try {
            tx.executeWithoutResult(status -> {
                paragraphs.generate(queued.getRoomId());
                jobs.findById(queued.getId()).orElseThrow().succeed();
                lockRoom(queued.getRoomId()).completeWeaving();
            });
        } catch (RuntimeException e) {
            log.warn("WEAVE_SCROLL job {} failed: {}", queued.getId(), e.getClass().getSimpleName());
            tx.executeWithoutResult(status ->
                    jobs.findById(queued.getId()).orElseThrow().fail(ErrorCode.AI_TEMPORARILY_UNAVAILABLE.name()));
        }
        return jobs.findById(queued.getId()).orElseThrow();
    }

    private Room lockRoom(String roomId) {
        return rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "존재하지 않는 방입니다."));
    }

    private Optional<AiJob> activeJob(String roomId) {
        return jobs.findFirstByRoomIdAndTypeAndStatusInOrderByCreatedAtDesc(roomId, AiJob.WEAVE_SCROLL, AiJob.ACTIVE_STATUSES);
    }
}
