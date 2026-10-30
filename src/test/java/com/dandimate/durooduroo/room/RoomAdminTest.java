package com.dandimate.durooduroo.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.job.service.JobWorker;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:room-admin-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "storage.local-root=build/test-uploads",
        "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000", "app.jobs.stale-check-interval-ms=3600000"
})
@AutoConfigureMockMvc
class RoomAdminTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JobWorker worker;

    @Test
    void 방장은_이름_날짜를_바꾸고_마감은_한_번만_24에서_72시간_사이로_바꾼다() throws Exception {
        String created = body(json(post("/api/v1/rooms"), null, "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String member = JsonPath.read(body(json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"지수\"," + CONSENT + "}")), "$.data.memberAccessToken");

        json(patch("/api/v1/rooms/" + roomId), member, "{\"name\":\"부산\"}").andExpect(status().isForbidden());
        json(patch("/api/v1/rooms/" + roomId), host, "{\"name\":\"" + "가".repeat(21) + "\"}").andExpect(status().isBadRequest());
        json(patch("/api/v1/rooms/" + roomId), host, "{\"deadlineAt\":\"" + OffsetDateTime.now().plusHours(10) + "\"}").andExpect(status().isBadRequest());

        json(patch("/api/v1/rooms/" + roomId), host, "{\"name\":\"부산 여행\",\"meetingDate\":\"2026-11-01\",\"deadlineAt\":\"" + OffsetDateTime.now().plusHours(60) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room.name").value("부산 여행"))
                .andExpect(jsonPath("$.data.room.meetingDate").value("2026-11-01"));
        json(patch("/api/v1/rooms/" + roomId), host, "{\"deadlineAt\":\"" + OffsetDateTime.now().plusHours(50) + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_ROOM_STATE"));
        mockMvc.perform(get("/api/v1/invites/" + invite)).andExpect(jsonPath("$.data.roomName").value("부산 여행"));
    }

    @Test
    void 방을_지우면_데이터가_사라지고_MEDIA_DELETE가_파일을_지운다() throws Exception {
        String created = body(json(post("/api/v1/rooms"), null, "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String member = JsonPath.read(body(json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"지수\"," + CONSENT + "}")), "$.data.memberAccessToken");

        String requested = body(json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), host,
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}"));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(new byte[]{1, 2, 3}));
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + host)).andExpect(status().isOk());
        json(post("/api/v1/photos/" + photoId + "/entries"), host, "{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"다들 웃은 사진\"}").andExpect(status().isCreated());
        Path roomDir = Path.of("build/test-uploads", roomId);
        assertThat(roomDir).exists();

        mockMvc.perform(delete("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + host)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + host)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/invites/" + invite)).andExpect(status().isNotFound());
        while (worker.runNext()) {
            // MEDIA_DELETE를 실행한다.
        }
        assertThat(Files.exists(roomDir)).isFalse();
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
