package com.dandimate.durooduroo.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TokenAuthTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository members;
    @Autowired
    private RoomRepository rooms;

    @Test
    void 토큰은_해시로만_저장되고_원문은_응답에서만_나간다() throws Exception {
        String created = createRoom("서연");
        String memberToken = JsonPath.read(created, "$.data.memberAccessToken");
        String inviteToken = JsonPath.read(created, "$.data.inviteToken");

        assertThat(members.findByAccessTokenHash(Tokens.hash(memberToken))).isPresent();
        assertThat(members.findByAccessTokenHash(memberToken)).isEmpty();
        assertThat(rooms.findByInviteTokenHash(Tokens.hash(inviteToken))).isPresent();
        assertThat(rooms.findByInviteTokenHash(inviteToken)).isEmpty();
    }

    @Test
    void 무효_토큰은_401_다른_방_토큰은_403() throws Exception {
        String roomA = createRoom("서연");
        String roomB = createRoom("지수");
        String roomAId = JsonPath.read(roomA, "$.data.room.id");
        String tokenB = JsonPath.read(roomB, "$.data.memberAccessToken");

        mockMvc.perform(get("/api/v1/rooms/" + roomAId).header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_MEMBER_TOKEN"));
        mockMvc.perform(get("/api/v1/rooms/" + roomAId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 초대_토큰을_재발급하면_기존_토큰은_즉시_무효가_된다() throws Exception {
        String created = createRoom("서연");
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String oldInvite = JsonPath.read(created, "$.data.inviteToken");

        String rotated = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/invite-token/rotate").header("Authorization", "Bearer " + host))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newInvite = JsonPath.read(rotated, "$.data.inviteToken");

        assertThat(newInvite).isNotEqualTo(oldInvite);
        mockMvc.perform(get("/api/v1/invites/" + oldInvite))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/invites/" + newInvite))
                .andExpect(status().isOk());
    }

    private String createRoom(String hostNickname) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"" + hostNickname + "\"," + CONSENT + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}
