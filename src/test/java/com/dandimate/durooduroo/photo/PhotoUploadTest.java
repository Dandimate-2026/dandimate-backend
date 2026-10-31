package com.dandimate.durooduroo.photo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.context.annotation.Import(com.dandimate.durooduroo.support.LegacyWorkflowSecurity.class)
@SpringBootTest(properties = "storage.local-root=build/test-uploads")
@AutoConfigureMockMvc
class PhotoUploadTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 업로드_URL로_올리고_complete가_저장소를_검증한다() throws Exception {
        String created = mockMvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}"))
                .andReturn().getResponse().getContentAsString();
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String other = JsonPath.read(mockMvc.perform(post("/api/v1/invites/" + invite + "/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"지수\"," + CONSENT + "}")).andReturn().getResponse().getContentAsString(), "$.data.memberAccessToken");

        // 형식 검증: HEIC/JPEG/PNG만, 2MB 이하
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/photos/upload-requests").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.gif\",\"contentType\":\"image/gif\",\"sizeBytes\":10,\"displayOrder\":1}]}"))
                .andExpect(status().isBadRequest());

        String requested = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/photos/upload-requests").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":10,\"displayOrder\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.uploads[0].requiredHeaders.Content-Type").value("image/jpeg"))
                .andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadPath = JsonPath.<String>read(requested, "$.data.uploads[0].uploadUrl").substring("http://localhost".length());

        // 올리기 전 complete는 검증 실패
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + host))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_VERIFICATION_FAILED"));

        // 업로드 URL 검증: 서명 위조 403, 다른 형식 415, 2MB 초과 413
        mockMvc.perform(put(uploadPath.replaceAll("signature=[^&]+", "signature=forged")).contentType("image/jpeg").content(new byte[]{1}))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(uploadPath).contentType("image/png").content(new byte[]{1}))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(put(uploadPath).contentType("image/jpeg").content(new byte[2_000_001]))
                .andExpect(status().isPayloadTooLarge());

        mockMvc.perform(put(uploadPath).contentType("image/jpeg").content(new byte[]{1, 2, 3})).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photo.status").value("READY"));
        // 완료 후에는 같은 URL로 덮어쓸 수 없다
        mockMvc.perform(put(uploadPath).contentType("image/jpeg").content(new byte[]{9})).andExpect(status().isConflict());
    }
}
