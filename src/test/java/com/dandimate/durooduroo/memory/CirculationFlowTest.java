package com.dandimate.durooduroo.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "storage.local-root=build/test-uploads")
@AutoConfigureMockMvc
class CirculationFlowTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 응답하기_전에는_타인의_이야기가_응답_JSON에_없다() throws Exception {
        String created = body(json(post("/api/v1/rooms"), null, "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String a = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String b = join(invite, "지수");
        String c = join(invite, "민호");

        // 꽃 색은 입장 순서대로 배정된다
        mockMvc.perform(get("/api/v1/invites/" + invite))
                .andExpect(jsonPath("$.data.flowers").value(org.hamcrest.Matchers.contains("AZALEA", "FORSYTHIA", "ROYAL_AZALEA")));

        String photoA = upload(roomId, a, 1);
        String photoB = upload(roomId, b, 1);
        upload(roomId, c, 1); // 업로드만 하고 첫 한마디·나중에가 없는 사진은 노출되지 않는다

        // 첫 한마디 검증: 업로더만, 5자 이상, 음성은 transcriptionId 필요
        entry(photoA, b, "FIRST", "TEXT", "남의 사진 첫 한마디").andExpect(status().isForbidden());
        entry(photoA, a, "FIRST", "TEXT", "짧아").andExpect(status().isBadRequest());
        entry(photoA, a, "FIRST", "VOICE", "음성인데 초안이 없음").andExpect(status().isBadRequest());
        entry(photoA, a, "FIRST", "TEXT", "막차 놓쳐서 역까지 뛰었잖아").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.entry.kind").value("FIRST"))
                .andExpect(jsonPath("$.data.entry.member.flowerCode").value("AZALEA"))
                .andExpect(jsonPath("$.data.askbackJobId").isNotEmpty());
        entry(photoA, a, "FIRST", "TEXT", "두 번째 첫 한마디").andExpect(status().isUnprocessableEntity());
        entry(photoA, a, "ADDON", "TEXT", "내 사진에 덧붙임").andExpect(status().isForbidden());
        // 첫 한마디 "나중에"는 사진을 올린 사람만
        mockMvc.perform(post("/api/v1/photos/" + photoB + "/first-entry/skip").header("Authorization", "Bearer " + a))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/photos/" + photoB + "/first-entry/skip").header("Authorization", "Bearer " + b))
                .andExpect(status().isOk());

        // C가 A 사진에 덧붙인다
        entry(photoA, c, "ADDON", "TEXT", "민호만 아는 비밀").andExpect(status().isCreated());

        // B: A 사진은 잠김, C의 덧붙임은 응답 어디에도 없다. 자기 사진은 한마디 없음으로 보인다.
        String locked = circulation(roomId, b);
        assertThat(locked).doesNotContain("민호만 아는 비밀");
        assertThat(JsonPath.<List<String>>read(locked, "$.data.cards[*].photo.id")).containsExactly(photoA, photoB);
        assertThat(JsonPath.<Boolean>read(locked, "$.data.cards[0].locked")).isTrue();
        assertThat(JsonPath.<String>read(locked, "$.data.cards[0].lockReason")).isEqualTo("RESPOND_FIRST");
        assertThat(JsonPath.<String>read(locked, "$.data.cards[0].firstEntry.transcript")).isEqualTo("막차 놓쳐서 역까지 뛰었잖아");
        assertThat(JsonPath.<List<?>>read(locked, "$.data.cards[0].otherEntries")).isEmpty();
        assertThat(JsonPath.<String>read(locked, "$.data.cards[1].myResponse.status")).isEqualTo("OWN_PHOTO");
        assertThat(JsonPath.<Object>read(locked, "$.data.cards[1].firstEntry")).isNull();

        // B가 패스하면 잠금이 풀린다
        mockMvc.perform(put("/api/v1/photos/" + photoA + "/passes/me").header("Authorization", "Bearer " + b)).andExpect(status().isOk());
        String passed = circulation(roomId, b);
        assertThat(JsonPath.<String>read(passed, "$.data.cards[0].myResponse.status")).isEqualTo("PASSED");
        assertThat(JsonPath.<Boolean>read(passed, "$.data.cards[0].locked")).isFalse();
        assertThat(JsonPath.<List<String>>read(passed, "$.data.cards[0].otherEntries[*].transcript")).containsExactly("민호만 아는 비밀");

        // 패스 후 덧붙이면 패스가 지워지고, 덧붙임은 2개까지
        entry(photoA, b, "ADDON", "TEXT", "나도 뛰었어").andExpect(status().isCreated());
        assertThat(JsonPath.<String>read(circulation(roomId, b), "$.data.cards[0].myResponse.status")).isEqualTo("ADDON");
        entry(photoA, b, "ADDON", "TEXT", "그날 비도 왔지").andExpect(status().isCreated());
        entry(photoA, b, "ADDON", "TEXT", "세 번째").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LIMIT_REACHED"));
        mockMvc.perform(put("/api/v1/photos/" + photoA + "/passes/me").header("Authorization", "Bearer " + b)).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/photos/" + photoB + "/passes/me").header("Authorization", "Bearer " + b)).andExpect(status().isForbidden());

        // A는 자기 사진의 모든 이야기를 본다
        String own = circulation(roomId, a);
        assertThat(JsonPath.<String>read(own, "$.data.cards[0].myResponse.status")).isEqualTo("OWN_PHOTO");
        assertThat(JsonPath.<List<?>>read(own, "$.data.cards[0].otherEntries")).hasSize(3);

        // 페이지
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/circulation").param("size", "1").header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.data.cards.length()").value(1))
                .andExpect(jsonPath("$.data.nextCursor").value("1"));

        // 사진을 지우면 종속 원문도 사라진다
        mockMvc.perform(delete("/api/v1/photos/" + photoA).header("Authorization", "Bearer " + a)).andExpect(status().isNoContent());
        String afterDelete = circulation(roomId, c);
        assertThat(afterDelete).doesNotContain("민호만 아는 비밀");
        assertThat(JsonPath.<List<String>>read(afterDelete, "$.data.cards[*].photo.id")).containsExactly(photoB);
    }

    private String join(String invite, String nickname) throws Exception {
        return JsonPath.read(body(json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"" + nickname + "\"," + CONSENT + "}")),
                "$.data.memberAccessToken");
    }

    private String upload(String roomId, String token, int order) throws Exception {
        String requested = body(json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), token,
                "{\"files\":[{\"clientFileId\":\"c" + order + "\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":" + order + "}]}")
                .andExpect(status().isCreated()));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(new byte[]{1, 2, 3}))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return photoId;
    }

    private ResultActions entry(String photoId, String token, String kind, String inputMode, String transcript) throws Exception {
        return json(post("/api/v1/photos/" + photoId + "/entries"), token,
                "{\"kind\":\"" + kind + "\",\"inputMode\":\"" + inputMode + "\",\"transcript\":\"" + transcript + "\"}");
    }

    private String circulation(String roomId, String token) throws Exception {
        return body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/circulation").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
