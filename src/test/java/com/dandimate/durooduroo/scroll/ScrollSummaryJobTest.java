package com.dandimate.durooduroo.scroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dandimate.durooduroo.ai.summary.ScrollSummaryAiService;
import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.job.repository.AiJobRepository;
import com.dandimate.durooduroo.job.service.JobWorker;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.context.annotation.Import(com.dandimate.durooduroo.support.LegacyWorkflowSecurity.class)
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:scroll-summary;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "storage.local-root=build/test-uploads", "app.demo-enabled=true", "app.jobs.retry-delay-ms=0",
        "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000", "app.jobs.stale-check-interval-ms=3600000"})
@AutoConfigureMockMvc
class ScrollSummaryJobTest {
    private static final String CONSENT = "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired MockMvc mvc;
    @Autowired JobWorker worker;
    @Autowired RoomRepository rooms;
    @Autowired AiJobRepository jobs;
    @MockitoBean ScrollSummaryAiService ai;

    private String[] wovenRoom() throws Exception {
        String created = mvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"planType\":\"FREE\",\"hostNickname\":\"서연\"," + CONSENT + "}")).andReturn().getResponse().getContentAsString();
        String roomId = JsonPath.read(created, "$.data.room.id"), token = JsonPath.read(created, "$.data.memberAccessToken");
        String requested = mvc.perform(post("/api/v1/rooms/" + roomId + "/photos/upload-requests").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}")).andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId"), uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(java.util.UUID.randomUUID().toString().getBytes()));
        mvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + token));
        mvc.perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"비 오던 날 다 같이 젖었어\"}"));
        mvc.perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"DEMO\"}"));
        drain();
        return new String[]{roomId, token, photoId};
    }

    private void drain() {
        while (worker.runNext()) { }
    }

    private String summary(String roomId) {
        return rooms.findById(roomId).orElseThrow().getScrollSummary();
    }

    @Test void 엮기가_끝나면_요약을_저장하고_사진을_지우면_요약도_다시_만든다() throws Exception {
        when(ai.summarize(any())).thenReturn(" 비 오던 날 다 같이 젖었던 모임이에요. ");
        String[] room = wovenRoom();
        assertThat(summary(room[0])).isEqualTo("비 오던 날 다 같이 젖었던 모임이에요.");

        // 사진이 하나도 안 남으면 지운 내용이 지도에 남지 않게 요약을 비운다.
        mvc.perform(delete("/api/v1/photos/" + room[2]).header("Authorization", "Bearer " + room[1]));
        drain();
        assertThat(summary(room[0])).isNull();
    }

    @Test void 요약이_너무_길거나_실패해도_두루마리는_완성된다() throws Exception {
        when(ai.summarize(any())).thenReturn("가".repeat(ScrollSummaryAiService.MAX_LENGTH + 1));
        String[] room = wovenRoom();
        assertThat(rooms.findById(room[0]).orElseThrow().getStatus()).isEqualTo(RoomStatus.GROWING);
        assertThat(summary(room[0])).isNull();
        // A scheduled worker can hold the job while drain() finds no queued work.
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            drain();
            assertThat(jobs.findFirstByRoomIdAndTypeAndStatusInOrderByCreatedAtDesc(room[0], AiJob.SCROLL_SUMMARY, java.util.List.of("FAILED"))).isPresent();
        });
    }
}
