/**
 * QUEUED 작업을 선점해 실행하는 Worker입니다 (명세 4장 Job 처리 규칙).
 * - SKIP LOCKED로 선점하고 실행 전에 RUNNING으로 바꾼다. 서버가 여러 대여도 같은 작업을 두 번 잡지 않는다.
 * - 실패하면 즉시 1회, retry-delay 후 1회, 총 2회 재시도한 뒤 FAILED로 끝낸다. PHOTO_DERIVE는 사진도 FAILED로 둔다. WEAVE_SCROLL은 방이 WEAVING에 남아 방장이 다시 엮을 수 있고,
 *   ASKBACK_ANALYZE는 질문 없이 조용히 끝난다(되묻기는 부가 기능이라 사용자에게 알리지 않는다).
 * - stale-after 동안 갱신이 없는 RUNNING 작업은 Worker가 죽은 것으로 보고 QUEUED로 되돌린다.
 */
package com.dandimate.durooduroo.job.service;

import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.job.repository.AiJobRepository;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JobWorker {

    static final int MAX_ATTEMPTS = 3;
    private static final List<String> HANDLED_TYPES = List.of(AiJob.WEAVE_SCROLL, AiJob.ASKBACK_ANALYZE, AiJob.REPLY_REWEAVE, AiJob.MEDIA_DELETE, AiJob.PHOTO_DERIVE);
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final AiJobRepository jobs;
    private final WeaveJobService weaveJobs;
    private final com.dandimate.durooduroo.askback.service.AskbackAnalysisService askbackAnalysis;
    private final com.dandimate.durooduroo.scroll.service.ReplyReweaveService replyReweave;
    private final TransactionTemplate tx;
    private final com.dandimate.durooduroo.storage.ObjectStorage storage;
    private final com.dandimate.durooduroo.photo.service.PhotoDeriveService photoDerive;
    private final com.dandimate.durooduroo.notification.service.RoomEvents roomEvents;
    private final Duration retryDelay;
    private final Duration staleAfter;

    public JobWorker(AiJobRepository jobs, WeaveJobService weaveJobs, com.dandimate.durooduroo.askback.service.AskbackAnalysisService askbackAnalysis,
                     com.dandimate.durooduroo.scroll.service.ReplyReweaveService replyReweave,
                     PlatformTransactionManager transactionManager, com.dandimate.durooduroo.storage.ObjectStorage storage,
                     com.dandimate.durooduroo.photo.service.PhotoDeriveService photoDerive, com.dandimate.durooduroo.notification.service.RoomEvents roomEvents,
                     @Value("${app.jobs.retry-delay-ms:5000}") long retryDelayMs,
                     @Value("${app.jobs.stale-after-ms:300000}") long staleAfterMs) {
        this.jobs = jobs;
        this.weaveJobs = weaveJobs;
        this.askbackAnalysis = askbackAnalysis;
        this.replyReweave = replyReweave;
        this.tx = new TransactionTemplate(transactionManager);
        this.storage = storage;
        this.photoDerive = photoDerive;
        this.roomEvents = roomEvents;
        this.retryDelay = Duration.ofMillis(retryDelayMs);
        this.staleAfter = Duration.ofMillis(staleAfterMs);
    }

    @Scheduled(fixedDelayString = "${app.jobs.poll-interval-ms:1000}")
    public void poll() {
        while (runNext()) {
            // 실행할 작업이 없을 때까지 이어서 처리한다.
        }
    }

    /** 작업 하나를 선점해 실행한다. 실행할 작업이 없으면 false. */
    public boolean runNext() {
        AiJob claimed = tx.execute(status -> jobs.findRunnable(HANDLED_TYPES, LocalDateTime.now(), PageRequest.of(0, 1))
                .stream().findFirst().map(job -> {
                    job.claim();
                    return job;
                }).orElse(null));
        if (claimed == null) return false;
        publishJob(claimed.getId());

        try {
            switch (claimed.getType()) {
                case AiJob.ASKBACK_ANALYZE -> askbackAnalysis.execute(claimed.getId());
                case AiJob.REPLY_REWEAVE -> replyReweave.execute(claimed.getId());
                case AiJob.MEDIA_DELETE -> deleteMedia(claimed);
                case AiJob.PHOTO_DERIVE -> photoDerive.execute(claimed.getId());
                default -> weaveJobs.execute(claimed.getId());
            }
        } catch (RuntimeException e) {
            // 요청·응답 원문은 남기지 않고 작업 ID와 예외 종류만 기록한다.
            log.warn("Job {} ({}) attempt {} failed: {}", claimed.getId(), claimed.getType(), claimed.getAttempts(), e.getClass().getSimpleName());
            tx.executeWithoutResult(status -> {
                AiJob job = jobs.findById(claimed.getId()).orElseThrow();
                String errorCode = ErrorCode.AI_TEMPORARILY_UNAVAILABLE.name();
                if (job.getAttempts() >= MAX_ATTEMPTS) {
                    job.fail(errorCode);
                    if (AiJob.PHOTO_DERIVE.equals(job.getType())) photoDerive.onFinalFailure(job.getTargetId());
                }
                else job.retryAfter(job.getAttempts() == 1 ? Duration.ZERO : retryDelay, errorCode);
            });
        }
        publishJob(claimed.getId());
        return true;
    }

    /** 방 삭제 후 저장소의 사진·음성(roomId/ 아래)을 지운다. */
    private void deleteMedia(AiJob job) {
        storage.deletePrefix(job.getTargetId());
        tx.executeWithoutResult(status -> jobs.findById(job.getId()).orElseThrow().succeed());
    }

    /** 공개 작업(되묻기 분석 제외)의 상태 변화를 방 전체에 알린다. */
    private void publishJob(String jobId) {
        jobs.findById(jobId).filter(job -> !AiJob.ASKBACK_ANALYZE.equals(job.getType()) && !AiJob.PHOTO_DERIVE.equals(job.getType())).ifPresent(job ->
                roomEvents.toRoom(job.getRoomId(), "job.updated", java.util.Map.of("jobId", job.getId(), "status", job.getStatus())));
    }

    @Scheduled(fixedDelayString = "${app.jobs.stale-check-interval-ms:60000}")
    public void requeueStale() {
        tx.executeWithoutResult(status -> jobs.findAllByStatusAndUpdatedAtBefore("RUNNING", LocalDateTime.now().minus(staleAfter))
                .forEach(job -> {
                    log.warn("Job {} was RUNNING without updates for {}; requeueing", job.getId(), staleAfter);
                    job.requeue();
                }));
    }
}
