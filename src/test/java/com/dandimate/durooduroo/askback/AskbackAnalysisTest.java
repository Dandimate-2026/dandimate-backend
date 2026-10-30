package com.dandimate.durooduroo.askback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.ai.askback.AskbackAiService;
import com.dandimate.durooduroo.job.service.JobWorker;
import com.dandimate.durooduroo.askback.domain.Askback;
import com.dandimate.durooduroo.askback.repository.AskbackRepository;
import com.dandimate.durooduroo.event.EventLogRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Worker를 직접 돌려 되묻기 분석의 대상·한도·검증 규칙을 확인한다. 다른 테스트의 Worker와 섞이지 않게 전용 DB를 쓴다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:askback-analysis-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "storage.local-root=build/test-uploads",
        "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000", "app.jobs.stale-check-interval-ms=3600000"
})
@AutoConfigureMockMvc
class AskbackAnalysisTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JobWorker worker;
    @Autowired private AskbackRepository askbacks;
    @Autowired private EventLogRepository eventLogs;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AskbackAiService ai;

    @Test
    void 첫_한마디가_확정되면_업로더에게_묻고_기억_안_나요를_누른_사람에게는_다시_묻지_않는다() throws Exception {
        Room room = room();
        String photo = publishedPhoto(room, "다들 웃은 사진");
        drain();

        Askback first = only(askbacks.findAllByPhotoId(photo));
        assertThat(first.getTargetMemberId()).isEqualTo(room.hostId);
        assertThat(first.getGapType()).isEqualTo("NO_ADDON");
        assertThat(eventLogs.findAllByRoomIdAndName(room.id, "askback_sent")).hasSize(1);

        mockMvc.perform(post("/api/v1/askbacks/" + first.getId() + "/forgot").header("Authorization", "Bearer " + room.host))
                .andExpect(status().isOk());
        // 지수가 덧붙이면 다시 분석한다. 서연은 FORGOT이라 제외되고, 원문을 남긴 지수에게 다른 유형으로 묻는다.
        entry(photo, room.member, "ADDON", "막차 놓쳐서 다 같이 뛰었지").andExpect(status().isCreated());
        drain();
        List<Askback> all = askbacks.findAllByPhotoId(photo);
        assertThat(all).hasSize(2);
        Askback second = all.stream().filter(a -> !a.getId().equals(first.getId())).findFirst().orElseThrow();
        assertThat(second.getTargetMemberId()).isEqualTo(room.memberId);
        assertThat(second.getGapType()).isNotEqualTo("NO_ADDON");

        // 사진당 최대 2개: 더 바뀌어도 새 질문은 없다
        mockMvc.perform(post("/api/v1/askbacks/" + second.getId() + "/forgot").header("Authorization", "Bearer " + room.member));
        entry(photo, room.member, "ADDON", "역에서 표도 잃어버렸어").andExpect(status().isCreated());
        drain();
        assertThat(askbacks.findAllByPhotoId(photo)).hasSize(2);
        assertThat(eventLogs.findAllByRoomIdAndName(room.id, "askback_skipped")).isNotEmpty();
    }

    @Test
    void 분석하는_사이_원문이_바뀌면_오래된_결과를_저장하지_않는다() throws Exception {
        Room room = room();
        String photo = publishedPhoto(room, "다들 웃은 사진");
        doAnswer(invocation -> {
            jdbc.update("update photos set source_revision = source_revision + 1 where id = ?", photo);
            return invocation.callRealMethod();
        }).when(ai).analyze(any());

        drain();

        assertThat(askbacks.findAllByPhotoId(photo)).isEmpty();
        assertThat(eventLogs.findAllByRoomIdAndName(room.id, "askback_skipped"))
                .anySatisfy(log -> assertThat(log.getProperties()).isEqualTo("reason=stale_revision"));
    }

    @Test
    void 마감_24시간_전부터는_분석_작업을_만들지_않는다() throws Exception {
        Room room = room();
        jdbc.update("update rooms set deadline_at = ? where id = ?", LocalDateTime.now().plusHours(23), room.id);
        String photo = uploadPhoto(room);
        entry(photo, room.host, "FIRST", "다들 웃은 사진").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.askbackJobId").doesNotExist());
    }

    private record Room(String id, String host, String hostId, String member, String memberId) {}

    private Room room() throws Exception {
        String created = body(mockMvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}")));
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String joined = body(mockMvc.perform(post("/api/v1/invites/" + invite + "/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"지수\"," + CONSENT + "}")));
        return new Room(JsonPath.read(created, "$.data.room.id"), JsonPath.read(created, "$.data.memberAccessToken"),
                JsonPath.read(created, "$.data.member.id"), JsonPath.read(joined, "$.data.memberAccessToken"), JsonPath.read(joined, "$.data.member.id"));
    }

    private String publishedPhoto(Room room, String firstEntry) throws Exception {
        String photo = uploadPhoto(room);
        entry(photo, room.host, "FIRST", firstEntry).andExpect(status().isCreated());
        return photo;
    }

    private String uploadPhoto(Room room) throws Exception {
        String requested = body(mockMvc.perform(post("/api/v1/rooms/" + room.id + "/photos/upload-requests").header("Authorization", "Bearer " + room.host)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}")));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(new byte[]{1, 2, 3}));
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + room.host)).andExpect(status().isOk());
        return photoId;
    }

    private ResultActions entry(String photoId, String token, String kind, String transcript) throws Exception {
        return mockMvc.perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"" + kind + "\",\"inputMode\":\"TEXT\",\"transcript\":\"" + transcript + "\"}"));
    }

    private void drain() {
        while (worker.runNext()) {
            // 대기 중인 분석 작업을 모두 실행한다.
        }
    }

    private Askback only(List<Askback> list) {
        assertThat(list).hasSize(1);
        return list.get(0);
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
