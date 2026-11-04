package com.dandimate.durooduroo.photo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.photo.image.HeicConverter;
import com.dandimate.durooduroo.photo.image.JpegSamples;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Worker가 도는 상태에서 HEIC complete는 변환을 기다려 READY를 돌려주고, 바로 첫 한마디를 남길 수 있다. */
@org.springframework.context.annotation.Import(com.dandimate.durooduroo.support.LegacyWorkflowSecurity.class)
@SpringBootTest(properties = {"storage.local-root=build/test-uploads", "app.jobs.poll-interval-ms=100", "app.photos.derive-wait-ms=10000"})
@AutoConfigureMockMvc
class HeicCompleteWaitTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HeicConverter converter;

    @Test
    void complete가_READY를_돌려주면_바로_첫_한마디를_남길_수_있다() throws Exception {
        when(converter.toJpeg(any())).thenReturn(JpegSamples.withExif());
        String created = mockMvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"planType\":\"FREE\",\"hostNickname\":\"서연\"," + CONSENT + "}"))
                .andReturn().getResponse().getContentAsString();
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String requested = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/photos/upload-requests").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.heic\",\"contentType\":\"image/heic\",\"sizeBytes\":10,\"displayOrder\":1}]}"))
                .andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadPath = JsonPath.<String>read(requested, "$.data.uploads[0].uploadUrl").substring("http://localhost".length());
        mockMvc.perform(put(uploadPath).contentType("image/heic").content(new byte[]{1, 2, 3})).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photo.status").value("READY"));
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"막차 놓쳐서 뛰었잖아\"}"))
                .andExpect(status().isCreated());
    }
}
