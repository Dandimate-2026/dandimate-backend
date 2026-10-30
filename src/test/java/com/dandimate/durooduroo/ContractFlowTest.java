package com.dandimate.durooduroo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** openapi.yaml의 방 홈·Photo.imageUrl·되묻기·두루마리 응답을 실제 흐름으로 확인한다. */
@SpringBootTest(properties = {"storage.local-root=build/test-uploads", "app.jobs.poll-interval-ms=50"})
@AutoConfigureMockMvc
class ContractFlowTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 홈_사진주소_되묻기_두루마리가_계약서_형식을_따른다() throws Exception {
        String created = body(json(post("/api/v1/rooms"), null, "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String a = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String b = JsonPath.read(body(json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"지수\"," + CONSENT + "}")), "$.data.memberAccessToken");

        // 첫 한마디 전: 홈은 needsFirstEntry=true
        String photoA = upload(roomId, a);
        mockMvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room.meetingDate").value("2026-10-24"))
                .andExpect(jsonPath("$.data.me.flowerCode").value("AZALEA"))
                .andExpect(jsonPath("$.data.myTodo.needsFirstEntry").value(true))
                .andExpect(jsonPath("$.data.myTodo.topAskback").isEmpty())
                .andExpect(jsonPath("$.data.allCompleted").value(false));

        entry(photoA, a, "FIRST", "다들 갑자기 웃었던 사진").andExpect(status().isCreated());

        // B의 홈: 응답할 사진 1장, 아직 전원 완료 아님
        mockMvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.data.myTodo.pendingCirculationCount").value(1))
                .andExpect(jsonPath("$.data.todayFlowers[0].entryKind").value("FIRST"))
                .andExpect(jsonPath("$.data.todayFlowers[0].flowerCode").value("AZALEA"));

        // imageUrl은 서명된 URL이고 그대로 열린다. 서명을 바꾸면 403.
        String cards = body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/circulation").header("Authorization", "Bearer " + b)));
        String imageUrl = JsonPath.read(cards, "$.data.cards[0].photo.imageUrl");
        assertThat(imageUrl).startsWith("http://localhost/api/v1/photos/" + photoA + "/image?expires=");
        String imagePath = imageUrl.substring("http://localhost".length());
        byte[] image = mockMvc.perform(get(imagePath)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(image).isEqualTo(PNG);
        mockMvc.perform(get(imagePath.replaceAll("signature=[^&]+", "signature=forged"))).andExpect(status().isForbidden());

        // 되묻기 데모 실행 → 업로더(A)가 받는다. B에게는 존재도 알리지 않는다.
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/askbacks/run").header("Authorization", "Bearer " + a))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.job.type").value("ASKBACK_ANALYZE"));
        String mine = body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/askbacks/me").header("Authorization", "Bearer " + a)));
        String askbackId = JsonPath.read(mine, "$.data.askbacks[0].id");
        assertThat(JsonPath.<String>read(mine, "$.data.askbacks[0].photo.id")).isEqualTo(photoA);
        assertThat(JsonPath.<String>read(mine, "$.data.askbacks[0].gapType")).isEqualTo("NO_ADDON");
        assertThat(JsonPath.<List<String>>read(mine, "$.data.askbacks[0].evidenceNicknames")).containsExactly("서연");
        mockMvc.perform(get("/api/v1/askbacks/" + askbackId).header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.data.myTodo.topAskback.id").value(askbackId))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        // 답변 → ASKBACK_ANSWER 원문. 다시 답하면 409 ASKBACK_CLOSED.
        json(post("/api/v1/askbacks/" + askbackId + "/answer"), a, "{\"inputMode\":\"TEXT\",\"transcript\":\"지수가 뒤로 넘어졌거든\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.askback.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.entry.kind").value("ASKBACK_ANSWER"))
                .andExpect(jsonPath("$.data.entry.photoId").value(photoA));
        json(post("/api/v1/askbacks/" + askbackId + "/answer"), a, "{\"inputMode\":\"TEXT\",\"transcript\":\"한 번 더\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ASKBACK_CLOSED"));

        // B가 패스하면 되묻기 답이 otherEntries에 보이고, 전원 완료가 된다
        mockMvc.perform(put("/api/v1/photos/" + photoA + "/passes/me").header("Authorization", "Bearer " + b)).andExpect(status().isOk());
        String unlocked = body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/circulation").header("Authorization", "Bearer " + b)));
        assertThat(JsonPath.<List<String>>read(unlocked, "$.data.cards[0].otherEntries[*].kind")).containsExactly("ASKBACK_ANSWER");
        mockMvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.data.allCompleted").value(true))
                .andExpect(jsonPath("$.data.members[0].responseRate").value(1.0))
                .andExpect(jsonPath("$.data.members[0].photoCount").value(1))
                .andExpect(jsonPath("$.data.members[0].storyCount").value(1));

        // 마감 전 두루마리는 409, 엮은 뒤에는 사진당 문단과 정확한 인용
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/scroll").header("Authorization", "Bearer " + b))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROOM_STATE"));
        String closed = body(json(post("/api/v1/rooms/" + roomId + "/close"), a, "{\"mode\":\"DEMO\"}").andExpect(status().isAccepted()));
        String jobId = JsonPath.read(closed, "$.data.job.id");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/jobs/" + jobId).header("Authorization", "Bearer " + a))
                        .andExpect(jsonPath("$.data.status").value("SUCCEEDED")));
        String scroll = body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/scroll").header("Authorization", "Bearer " + b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.memberCount").value(2))
                .andExpect(jsonPath("$.data.participantCount").value(1))
                .andExpect(jsonPath("$.data.paragraphs.length()").value(1))
                .andExpect(jsonPath("$.data.paragraphs[0].photo.id").value(photoA))
                .andExpect(jsonPath("$.data.paragraphs[0].aiGenerated").value(true))
                .andExpect(jsonPath("$.data.paragraphs[0].sources.length()").value(2)));
        String paragraphBody = JsonPath.read(scroll, "$.data.paragraphs[0].body");
        assertThat(JsonPath.<List<String>>read(scroll, "$.data.paragraphs[0].sources[*].quote"))
                .containsExactly("다들 갑자기 웃었던 사진", "지수가 뒤로 넘어졌거든")
                .allSatisfy(quote -> assertThat(paragraphBody).contains(quote));
    }

    private String upload(String roomId, String token) throws Exception {
        String requested = body(json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), token,
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}"));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        mockMvc.perform(multipart("/api/v1/photos/" + photoId + "/complete")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return photoId;
    }

    private ResultActions entry(String photoId, String token, String kind, String transcript) throws Exception {
        return json(post("/api/v1/photos/" + photoId + "/entries"), token,
                "{\"kind\":\"" + kind + "\",\"inputMode\":\"TEXT\",\"transcript\":\"" + transcript + "\"}");
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
