package com.dandimate.durooduroo.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.memory.audio.VoiceSamples;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 가짜 STT(local 프로필 기본값)로 음성 입력 흐름 전체를 확인한다. */
@org.springframework.context.annotation.Import(com.dandimate.durooduroo.support.LegacyWorkflowSecurity.class)
@SpringBootTest(properties = "storage.local-root=build/test-uploads")
@AutoConfigureMockMvc
class VoiceFlowTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 녹음_받아쓰기_수정_확정까지_음성은_서버가_길이를_재고_초안은_한_번만_쓴다() throws Exception {
        String created = body(mockMvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}")));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String other = JsonPath.read(body(mockMvc.perform(post("/api/v1/invites/" + invite + "/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"지수\"," + CONSENT + "}"))), "$.data.memberAccessToken");
        String photoId = uploadPhoto(roomId, host);

        // 길이·형식 검증: 첫 한마디는 5~60초
        transcribe(photoId, host, "FIRST", "audio/wav", VoiceSamples.wav(2)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MIN_DURATION_NOT_MET"));
        transcribe(photoId, host, "FIRST", "audio/wav", VoiceSamples.wav(61)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MAX_DURATION_EXCEEDED"));
        transcribe(photoId, host, "FIRST", "text/plain", new byte[]{1}).andExpect(status().isUnsupportedMediaType());
        transcribe(photoId, other, "FIRST", "audio/wav", VoiceSamples.wav(6)).andExpect(status().isForbidden());

        String draft = body(transcribe(photoId, host, "FIRST", "audio/wav", VoiceSamples.wav(6))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transcript").isNotEmpty())
                .andExpect(jsonPath("$.data.audioAssetId").isNotEmpty()));
        String transcriptionId = JsonPath.read(draft, "$.data.transcriptionId");

        // 사용자가 고친 문장으로 확정 → 음성은 서명 URL로 열린다
        String entry = body(entry(photoId, host, "{\"kind\":\"FIRST\",\"inputMode\":\"VOICE\",\"transcriptionId\":\"" + transcriptionId
                + "\",\"transcript\":\"이때 막차 놓쳐서 뛰었잖아\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.entry.inputMode").value("VOICE"))
                .andExpect(jsonPath("$.data.entry.transcript").value("이때 막차 놓쳐서 뛰었잖아")));
        String audioUrl = JsonPath.read(entry, "$.data.entry.audioUrl");
        assertThat(audioUrl).contains("/api/v1/entries/").contains("/audio?expires=");
        byte[] audio = mockMvc.perform(get(audioUrl.substring("http://localhost".length()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(audio).isEqualTo(VoiceSamples.wav(6));

        // 같은 초안을 다시 쓰거나, 남의 초안·없는 초안으로 확정할 수 없다
        String photo2 = uploadPhoto(roomId, host);
        entry(photo2, host, "{\"kind\":\"FIRST\",\"inputMode\":\"VOICE\",\"transcriptionId\":\"" + transcriptionId + "\",\"transcript\":\"두 번째 사진 이야기\"}")
                .andExpect(status().isBadRequest());
        entry(photo2, host, "{\"kind\":\"FIRST\",\"inputMode\":\"VOICE\",\"transcriptionId\":\"no-such-draft-0000\",\"transcript\":\"두 번째 사진 이야기\"}")
                .andExpect(status().isBadRequest());

        // 덧붙임 음성은 1~30초
        transcribe(photoId, other, "ADDON", "audio/wav", VoiceSamples.wav(31)).andExpect(status().isUnprocessableEntity());
        transcribe(photoId, other, "ADDON", "audio/wav", VoiceSamples.wav(3)).andExpect(status().isCreated());
    }

    private ResultActions transcribe(String photoId, String token, String kind, String contentType, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/v1/photos/" + photoId + "/transcriptions")
                .file(new MockMultipartFile("file", "voice.bin", contentType, bytes)).param("kind", kind)
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions entry(String photoId, String token, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/photos/" + photoId + "/entries").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String uploadPhoto(String roomId, String token) throws Exception {
        String requested = body(mockMvc.perform(post("/api/v1/rooms/" + roomId + "/photos/upload-requests").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}")));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(new byte[]{1, 2, 3})).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        return photoId;
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
