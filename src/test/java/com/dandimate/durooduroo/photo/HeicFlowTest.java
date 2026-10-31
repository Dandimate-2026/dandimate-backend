package com.dandimate.durooduroo.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.job.service.JobWorker;
import com.dandimate.durooduroo.photo.image.HeicConverter;
import com.dandimate.durooduroo.photo.image.JpegSamples;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HEIC 업로드: PROCESSING → (파생본 생성) → READY, 변환 실패 → FAILED → complete로 재시도. Worker는 직접 돌린다. */
@SpringBootTest(properties = {"storage.local-root=build/test-uploads", "app.jobs.retry-delay-ms=0",
        "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000", "app.jobs.stale-check-interval-ms=3600000"})
@AutoConfigureMockMvc
class HeicFlowTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JobWorker worker;
    @Autowired private com.dandimate.durooduroo.photo.repository.PhotoRepository photos;
    @MockitoBean private HeicConverter converter;

    @Test
    void HEIC는_EXIF를_지운_JPEG_파생본을_만들고_실패하면_재시도할_수_있다() throws Exception {
        String created = body(json(post("/api/v1/rooms"), null, "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");

        // 1) 변환 실패 3회 → FAILED
        when(converter.toJpeg(any())).thenThrow(new IllegalStateException("broken heic"));
        String photo = uploadHeic(roomId, host);
        complete(photo, host).andExpect(jsonPath("$.data.photo.status").value("PROCESSING"));
        json(post("/api/v1/photos/" + photo + "/entries"), host, "{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"변환 전 첫 한마디\"}")
                .andExpect(status().isConflict());
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            drain();
            assertThat(photos.findById(photo).orElseThrow().getStatus()).isEqualTo("FAILED");
        });
        assertThat(photos.findById(photo).orElseThrow().getFileUrl()).isNotNull();
        complete(photo, host).andExpect(jsonPath("$.data.photo.status").value("PROCESSING"));

        // 2) 재시도에서 성공 → READY, 파생본에는 EXIF가 없다
        doReturn(JpegSamples.withExif()).when(converter).toJpeg(any());
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            drain();
            assertThat(photos.findById(photo).orElseThrow().getStatus()).isEqualTo("READY");
        });
        String cards = body(json(post("/api/v1/photos/" + photo + "/entries"), host,
                "{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"변환 뒤 첫 한마디\"}").andExpect(status().isCreated()));
        assertThat(cards).contains("FIRST");
        String circulation = body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/circulation").header("Authorization", "Bearer " + host)));
        String imageUrl = JsonPath.read(circulation, "$.data.cards[0].photo.imageUrl");
        var response = mockMvc.perform(get(imageUrl.substring("http://localhost".length()))).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        String image = new String(response.getContentAsByteArray(), StandardCharsets.ISO_8859_1);
        assertThat(image).doesNotContain("Exif").doesNotContain("GPS").contains("JFIF");
    }

    private String uploadHeic(String roomId, String token) throws Exception {
        String requested = body(json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), token,
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.heic\",\"contentType\":\"image/heic\",\"sizeBytes\":10,\"displayOrder\":1}]}"));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/heic").content(new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p'}))
                .andExpect(status().isOk());
        return photoId;
    }

    private ResultActions complete(String photoId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
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
