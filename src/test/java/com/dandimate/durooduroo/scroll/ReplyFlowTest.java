package com.dandimate.durooduroo.scroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.job.service.JobWorker;
import com.dandimate.durooduroo.scroll.domain.ParagraphVersion;
import com.dandimate.durooduroo.scroll.repository.ParagraphVersionRepository;
import com.dandimate.durooduroo.scroll.repository.ReplyRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 규칙 기반 AI로 답가 분류·부분 재엮기·삭제 복구·원문 삭제 복구를 확인한다. Worker는 직접 돌린다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reply-flow-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "storage.local-root=build/test-uploads",
        "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000", "app.jobs.stale-check-interval-ms=3600000"
})
@AutoConfigureMockMvc
class ReplyFlowTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JobWorker worker;
    @Autowired private ReplyRepository replies;
    @Autowired private ParagraphVersionRepository versions;

    @Test
    void 반응은_본문에_넣지_않고_새_사실은_엮고_지우면_복구한다() throws Exception {
        String created = body(json(post("/api/v1/rooms"), null, "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String member = JsonPath.read(body(json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"지수\"," + CONSENT + "}")), "$.data.memberAccessToken");

        String photo = uploadPhoto(roomId, host);
        entry(photo, host, "FIRST", "막차 놓쳐서 뛰었잖아");
        String addon = JsonPath.read(entry(photo, member, "ADDON", "다리가 풀리는 줄 알았어"), "$.data.entry.id");
        json(post("/api/v1/rooms/" + roomId + "/close"), host, "{\"mode\":\"DEMO\"}").andExpect(status().isAccepted());
        drain();

        String paragraphId = JsonPath.read(scroll(roomId, host), "$.data.paragraphs[0].id");

        // 답가는 GROWING에서만, 반응은 본문에 넣지 않는다
        String reaction = reply(paragraphId, member, "ㅋㅋㅋㅋ");
        drain();
        assertThat(replies.findById(reaction).orElseThrow().getStatus()).isEqualTo("REACTION");
        assertThat(JsonPath.<Integer>read(scroll(roomId, host), "$.data.paragraphs[0].version")).isEqualTo(1);

        // 새 사실은 기존 인용을 유지한 채 엮고 version이 오른다
        String fact = reply(paragraphId, member, "역에 도착하고 나서 표를 잃어버렸어");
        drain();
        assertThat(replies.findById(fact).orElseThrow().getStatus()).isEqualTo("APPLIED");
        String applied = scroll(roomId, host);
        assertThat(JsonPath.<Integer>read(applied, "$.data.paragraphs[0].version")).isEqualTo(2);
        assertThat(JsonPath.<String>read(applied, "$.data.paragraphs[0].body")).contains("표를 잃어버렸어").contains("다리가 풀리는 줄 알았어");
        assertThat(JsonPath.<List<String>>read(applied, "$.data.paragraphs[0].sources[*].quote"))
                .containsExactly("막차 놓쳐서 뛰었잖아", "다리가 풀리는 줄 알았어", "역에 도착하고 나서 표를 잃어버렸어");
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/notifications").header("Authorization", "Bearer " + member))
                .andExpect(jsonPath("$.data.notifications[?(@.type == 'REPLY_APPLIED')]").isNotEmpty());

        // 욕설은 거절
        String unsafe = reply(paragraphId, member, "이 씨발 진짜");
        drain();
        assertThat(replies.findById(unsafe).orElseThrow().getStatus()).isEqualTo("REJECTED");

        // 남의 답가는 지울 수 없고, 반영된 답가를 지우면 그 내용 없이 복구된다
        mockMvc.perform(delete("/api/v1/replies/" + fact).header("Authorization", "Bearer " + host)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/replies/" + fact).header("Authorization", "Bearer " + member))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.job.type").value("REPLY_REWEAVE"));
        drain();
        String restored = scroll(roomId, host);
        assertThat(JsonPath.<Integer>read(restored, "$.data.paragraphs[0].version")).isEqualTo(3);
        assertThat(JsonPath.<String>read(restored, "$.data.paragraphs[0].body")).doesNotContain("표를 잃어버렸어");

        // GROWING에서 원문을 지우면 문단을 다시 만든다
        mockMvc.perform(delete("/api/v1/entries/" + addon).header("Authorization", "Bearer " + member)).andExpect(status().isNoContent());
        drain();
        String rebuilt = scroll(roomId, host);
        assertThat(JsonPath.<String>read(rebuilt, "$.data.paragraphs[0].body")).doesNotContain("다리가 풀리는 줄 알았어").contains("막차 놓쳐서 뛰었잖아");
        assertThat(versions.findAllByParagraphIdOrderByVersionAsc(paragraphId)).extracting(ParagraphVersion::getReason)
                .containsExactly("INITIAL", "REPLY_ADDED", "REPLY_DELETED", "ENTRY_DELETED");
    }

    private String reply(String paragraphId, String token, String transcript) throws Exception {
        String accepted = body(json(post("/api/v1/paragraphs/" + paragraphId + "/replies"), token, "{\"inputMode\":\"TEXT\",\"transcript\":\"" + transcript + "\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.job.type").value("REPLY_REWEAVE"))
                .andExpect(jsonPath("$.data.reply.status").value("PENDING")));
        return JsonPath.read(accepted, "$.data.reply.id");
    }

    private String scroll(String roomId, String token) throws Exception {
        return body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/scroll").header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private String entry(String photoId, String token, String kind, String transcript) throws Exception {
        return body(json(post("/api/v1/photos/" + photoId + "/entries"), token,
                "{\"kind\":\"" + kind + "\",\"inputMode\":\"TEXT\",\"transcript\":\"" + transcript + "\"}").andExpect(status().isCreated()));
    }

    private String uploadPhoto(String roomId, String token) throws Exception {
        String requested = body(json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), token,
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}"));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(new byte[]{1, 2, 3}));
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        return photoId;
    }

    private void drain() {
        while (worker.runNext()) {
            // 대기 중인 작업을 모두 실행한다.
        }
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
