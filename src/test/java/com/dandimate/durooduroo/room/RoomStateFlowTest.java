package com.dandimate.durooduroo.room;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.awaitility.Awaitility.await;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.context.annotation.Import(com.dandimate.durooduroo.support.LegacyWorkflowSecurity.class)
@SpringBootTest(properties = {"storage.local-root=build/test-uploads", "app.jobs.poll-interval-ms=50"})
@AutoConfigureMockMvc
class RoomStateFlowTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 방은_모으기_돌려보기_엮기_자라기_순서로_전이되고_상태별로_쓰기를_막는다() throws Exception {
        String created = body(perform(post("/api/v1/rooms"), "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.room.status").value("COLLECTING"))
                .andExpect(jsonPath("$.data.member.role").value("HOST")));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");

        String joined = body(perform(post("/api/v1/invites/" + invite + "/join"), "{\"nickname\":\"지수\"," + CONSENT + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.member.role").value("MEMBER")));
        String member = JsonPath.read(joined, "$.data.memberAccessToken");

        String photoId = uploadPhoto(roomId, host);

        // 노출 전 사진에는 덧붙일 수 없다 (존재를 알리지 않음)
        perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + member), "{\"kind\":\"ADDON\",\"inputMode\":\"TEXT\",\"transcript\":\"나도 기억나\"}")
                .andExpect(status().isNotFound());
        // 마감도 CIRCULATING 전에는 불가
        perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + host), "{\"mode\":\"DEMO\"}")
                .andExpect(status().isConflict());

        // 업로더의 첫 한마디 확정 → CIRCULATING
        perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + host), "{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"막차 놓쳐서 뛰었잖아\"}")
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + host))
                .andExpect(jsonPath("$.data.room.status").value("CIRCULATING"));
        perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + member), "{\"kind\":\"ADDON\",\"inputMode\":\"TEXT\",\"transcript\":\"다리가 풀리는 줄 알았어\"}")
                .andExpect(status().isCreated());

        // mode는 필수, 참여자는 마감 불가, 방장 마감 → 엮기 성공 → GROWING
        perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + host), "{}")
                .andExpect(status().isBadRequest());
        perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + member), "{\"mode\":\"EARLY\"}")
                .andExpect(status().isForbidden());
        String closed = body(perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + host), "{\"mode\":\"DEMO\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.job.type").value("WEAVE_SCROLL")));
        String jobId = JsonPath.read(closed, "$.data.job.id");
        awaitJob(jobId, member, "SUCCEEDED");
        mockMvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + host))
                .andExpect(jsonPath("$.data.room.status").value("GROWING"));

        mockMvc.perform(get("/api/v1/jobs/" + jobId).header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isUnauthorized());

        // GROWING: 덧붙임·업로드 불가, 입장은 가능 (FR-A5), 재마감 불가
        perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + member), "{\"kind\":\"ADDON\",\"inputMode\":\"TEXT\",\"transcript\":\"하나 더\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROOM_STATE"));
        perform(post("/api/v1/invites/" + invite + "/join"), "{\"nickname\":\"늦은친구\"," + CONSENT + "}")
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/invites/" + invite))
                .andExpect(jsonPath("$.data.status").value("GROWING"))
                .andExpect(jsonPath("$.data.meetingDate").value("2026-10-24"));
        perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + host), "{\"mode\":\"EARLY\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROOM_STATE"));

        // 다시 엮기는 방장만, 방당 10회까지
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/weave-jobs").header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 10; i++) {
            String reweave = body(mockMvc.perform(post("/api/v1/rooms/" + roomId + "/weave-jobs").header("Authorization", "Bearer " + host))
                    .andExpect(status().isAccepted()));
            awaitJob(JsonPath.read(reweave, "$.data.job.id"), host, "SUCCEEDED");
        }
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/weave-jobs").header("Authorization", "Bearer " + host))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LIMIT_REACHED"));
    }

    private void awaitJob(String jobId, String token, String expected) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/jobs/" + jobId).header("Authorization", "Bearer " + token))
                        .andExpect(jsonPath("$.data.status").value(expected)));
    }

    private String uploadPhoto(String roomId, String token) throws Exception {
        String requested = body(perform(post("/api/v1/rooms/" + roomId + "/photos/upload-requests").header("Authorization", "Bearer " + token),
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}")
                .andExpect(status().isCreated()));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(new byte[]{1, 2, 3}))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return photoId;
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
