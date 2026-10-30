package com.dandimate.durooduroo.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"storage.local-root=build/test-uploads", "app.jobs.poll-interval-ms=50"})
@AutoConfigureMockMvc
class RoomEventsTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 방_이벤트는_방_전체에_대상자_전용_이벤트는_대상자에게만_간다() throws Exception {
        String created = body(json(post("/api/v1/rooms"), null, "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String member = JsonPath.read(body(json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"지수\"," + CONSENT + "}")), "$.data.memberAccessToken");

        MockHttpServletResponse hostStream = subscribe(roomId, host);
        MockHttpServletResponse memberStream = subscribe(roomId, member);

        String photo = uploadPhoto(roomId, host);
        json(post("/api/v1/photos/" + photo + "/entries"), host, "{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"다들 웃은 사진\"}")
                .andExpect(status().isCreated());

        // 방 전체: 사진 노출·원문 확정. 본문·사진 URL은 싣지 않는다.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(memberStream.getContentAsString()).contains("event:photo.published").contains("event:entry.created");
            // 업로더 전용(photo.uploaded)·되묻기 대상자(서연) 전용 이벤트는 서연에게만
            assertThat(hostStream.getContentAsString()).contains("event:photo.uploaded").contains("event:askback.created").contains("event:notification.created");
        });
        assertThat(memberStream.getContentAsString())
                .doesNotContain("photo.uploaded").doesNotContain("askback.created").doesNotContain("다들 웃은 사진").doesNotContain("imageUrl");
    }

    private MockHttpServletResponse subscribe(String roomId, String token) throws Exception {
        String issued = body(mockMvc.perform(post("/api/v1/rooms/" + roomId + "/event-token").header("Authorization", "Bearer " + token)));
        String eventsUrl = JsonPath.read(issued, "$.data.eventsUrl");
        return mockMvc.perform(get(eventsUrl.substring("http://localhost".length())))
                .andExpect(request().asyncStarted()).andReturn().getResponse();
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

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
