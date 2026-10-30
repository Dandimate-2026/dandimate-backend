package com.dandimate.durooduroo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.dandimate.durooduroo.ai.domain.AiJob;
import com.dandimate.durooduroo.ai.repository.AiJobRepository;
import com.dandimate.durooduroo.ai.service.AiParagraphService;
import com.dandimate.durooduroo.ai.service.JobWorker;
import com.dandimate.durooduroo.ai.service.WeaveJobService;
import com.dandimate.durooduroo.askback.domain.Askback;
import com.dandimate.durooduroo.askback.repository.AskbackRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.room.service.DeadlineScheduler;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 스케줄 실행은 사실상 끄고(1시간 주기) Worker·스케줄러를 직접 호출해 재시도와 시각을 결정적으로 검증한다.
 * 다른 테스트 컨텍스트의 Worker가 같은 H2의 작업을 가져가지 않도록 전용 DB를 쓴다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-worker-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "app.jobs.poll-interval-ms=3600000",
        "app.jobs.deadline-check-interval-ms=3600000",
        "app.jobs.stale-check-interval-ms=3600000",
        "app.jobs.retry-delay-ms=60000"
})
class JobWorkerTest {

    @Autowired private JobWorker worker;
    @Autowired private DeadlineScheduler deadlineScheduler;
    @Autowired private WeaveJobService weaveJobs;
    @Autowired private AiJobRepository jobs;
    @Autowired private RoomRepository rooms;
    @Autowired private AskbackRepository askbacks;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AiParagraphService paragraphs;

    /** 같은 H2를 쓰는 다른 테스트가 남긴 대기 작업을 Worker가 먼저 잡지 않도록 비운다. */
    @BeforeEach
    void clearActiveJobs() {
        jdbc.update("delete from ai_jobs where status in ('QUEUED', 'RUNNING')");
    }

    @Test
    void 실패하면_즉시_1회_지연_후_1회_재시도하고_세_번째에_성공한다() {
        String roomId = circulatingRoom();
        AiJob queued = weaveJobs.close(roomId);
        assertThat(queued.getStatus()).isEqualTo("QUEUED");
        doThrow(new IllegalStateException("ai down")).doThrow(new IllegalStateException("ai down")).doCallRealMethod()
                .when(paragraphs).generate(anyString());

        assertThat(worker.runNext()).isTrue();               // 1회차 실패 → 즉시 재시도 대기
        assertThat(job(queued).getStatus()).isEqualTo("QUEUED");
        assertThat(worker.runNext()).isTrue();               // 2회차 실패 → 60초 뒤 재시도
        assertThat(job(queued).getNextRunAt()).isAfter(LocalDateTime.now().plusSeconds(50));
        assertThat(worker.runNext()).isFalse();              // 아직 시각이 안 됨

        makeDue(queued.getId());
        assertThat(worker.runNext()).isTrue();               // 3회차 성공
        AiJob done = job(queued);
        assertThat(done.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(done.getAttempts()).isEqualTo(3);
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.GROWING);
    }

    @Test
    void 최종_실패하면_WEAVING에_남고_방장이_다시_엮을_수_있다() {
        String roomId = circulatingRoom();
        AiJob queued = weaveJobs.close(roomId);
        doThrow(new IllegalStateException("ai down")).when(paragraphs).generate(anyString());

        worker.runNext();
        worker.runNext();
        makeDue(queued.getId());
        worker.runNext();

        AiJob failed = job(queued);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getAttempts()).isEqualTo(3);
        assertThat(failed.getErrorCode()).isEqualTo("AI_TEMPORARILY_UNAVAILABLE");
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.WEAVING);

        doCallRealMethod().when(paragraphs).generate(anyString());
        AiJob retry = weaveJobs.reweave(roomId);
        worker.runNext();
        assertThat(job(retry).getStatus()).isEqualTo("SUCCEEDED");
        Room room = rooms.findById(roomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.GROWING);
        assertThat(room.getRegenerationCount()).isEqualTo(1);
    }

    @Test
    void 오래_갱신되지_않은_RUNNING_작업은_QUEUED로_되돌린다() {
        AiJob queued = weaveJobs.close(circulatingRoom());
        jdbc.update("update ai_jobs set status = 'RUNNING', updated_at = ? where id = ?", LocalDateTime.now().minusMinutes(10), queued.getId());

        worker.requeueStale();

        assertThat(job(queued).getStatus()).isEqualTo("QUEUED");
    }

    @Test
    void 마감하면_미응답_되묻기는_EXPIRED가_된다() {
        String roomId = circulatingRoom();
        Askback askback = askbacks.save(Askback.create(roomId, "photo-1", "member-1", "MISSING_REASON", "그때 왜 웃었어요?", java.util.List.of(), LocalDateTime.now().plusDays(1)));

        weaveJobs.close(roomId);

        assertThat(askbacks.findById(askback.getId()).orElseThrow().getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void 마감_시각이_지나면_돌려보는_방은_엮기를_시작한다() {
        String roomId = circulatingRoom();
        moveDeadlineToPast(roomId);

        deadlineScheduler.closeDueRooms();

        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.WEAVING);
        assertThat(jobs.findFirstByRoomIdAndTypeAndStatusInOrderByCreatedAtDesc(roomId, AiJob.WEAVE_SCROLL, AiJob.ACTIVE_STATUSES)).isPresent();
    }

    @Test
    void 원문_없이_마감되면_한_번_24시간_연장하고_그래도_없으면_빈_두루마리로_끝낸다() {
        String roomId = rooms.save(Room.create("빈 방")).getId();
        moveDeadlineToPast(roomId);
        LocalDateTime before = rooms.findById(roomId).orElseThrow().getDeadlineAt();

        deadlineScheduler.closeDueRooms();
        Room extended = rooms.findById(roomId).orElseThrow();
        assertThat(extended.getStatus()).isEqualTo(RoomStatus.COLLECTING);
        assertThat(extended.isDeadlineExtended()).isTrue();
        assertThat(extended.getDeadlineAt()).isEqualTo(before.plusHours(24));

        moveDeadlineToPast(roomId);
        deadlineScheduler.closeDueRooms();
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.GROWING);
        assertThat(jobs.findFirstByRoomIdAndTypeAndStatusInOrderByCreatedAtDesc(roomId, AiJob.WEAVE_SCROLL, AiJob.ACTIVE_STATUSES)).isEmpty();
    }

    private String circulatingRoom() {
        Room room = Room.create("경주");
        room.startCirculating();
        return rooms.save(room).getId();
    }

    private AiJob job(AiJob job) {
        return jobs.findById(job.getId()).orElseThrow();
    }

    private void makeDue(String jobId) {
        jdbc.update("update ai_jobs set next_run_at = ? where id = ?", LocalDateTime.now().minusSeconds(1), jobId);
    }

    private void moveDeadlineToPast(String roomId) {
        jdbc.update("update rooms set deadline_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), roomId);
    }
}
