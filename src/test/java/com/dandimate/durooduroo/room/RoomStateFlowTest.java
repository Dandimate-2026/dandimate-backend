package com.dandimate.durooduroo.room;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "storage.local-root=build/test-uploads")
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

        // COLLECTING: 덧붙임은 아직 불가
        perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + member), "{\"transcript\":\"나도 기억나\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROOM_STATE"));
        // 마감도 CIRCULATING 전에는 불가
        perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + host), "{\"mode\":\"DEMO\"}")
                .andExpect(status().isConflict());

        // 업로더의 첫 한마디 확정 → CIRCULATING
        perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + host), "{\"transcript\":\"막차 놓쳐서 뛰었잖아\"}")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + host))
                .andExpect(jsonPath("$.data.room.status").value("CIRCULATING"));
        perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + member), "{\"transcript\":\"다리가 풀리는 줄 알았어\"}")
                .andExpect(status().isOk());

        // mode는 필수, 참여자는 마감 불가, 방장 마감 → 엮기 성공 → GROWING
        perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + host), "{}")
                .andExpect(status().isBadRequest());
        perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + member), "{\"mode\":\"EARLY\"}")
                .andExpect(status().isForbidden());
        String closed = body(perform(post("/api/v1/rooms/" + roomId + "/close").header("Authorization", "Bearer " + host), "{\"mode\":\"DEMO\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.job.type").value("WEAVE_SCROLL"))
                .andExpect(jsonPath("$.data.job.status").value("SUCCEEDED")));
        String jobId = JsonPath.read(closed, "$.data.job.id");
        mockMvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + host))
                .andExpect(jsonPath("$.data.room.status").value("GROWING"));

        mockMvc.perform(get("/api/v1/jobs/" + jobId).header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isUnauthorized());

        // GROWING: 덧붙임·업로드 불가, 입장은 가능 (FR-A5), 재마감 불가
        perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + member), "{\"transcript\":\"하나 더\"}")
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
            mockMvc.perform(post("/api/v1/rooms/" + roomId + "/weave-jobs").header("Authorization", "Bearer " + host))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.job.status").value("SUCCEEDED"));
        }
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/weave-jobs").header("Authorization", "Bearer " + host))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LIMIT_REACHED"));
    }

    private String uploadPhoto(String roomId, String token) throws Exception {
        String requested = body(perform(post("/api/v1/rooms/" + roomId + "/photos/upload-requests").header("Authorization", "Bearer " + token),
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}")
                .andExpect(status().isOk()));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        mockMvc.perform(multipart("/api/v1/photos/" + photoId + "/complete")
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer " + token))
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
