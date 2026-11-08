package com.dandimate.durooduroo.reviewerdemo;

import com.dandimate.durooduroo.common.auth.AccountPrincipal;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.reviewerdemo.domain.ReviewerDemoSession;
import com.dandimate.durooduroo.reviewerdemo.repository.ReviewerDemoSessionRepository;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.scroll.repository.ScrollRepository;
import com.dandimate.durooduroo.user.service.KakaoAccountService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reviewer-demo;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "storage.local-root=build/reviewer-demo-test-uploads",
        "app.reviewer-demo.enabled=true",
        "app.jobs.poll-interval-ms=3600000",
        "app.jobs.deadline-check-interval-ms=3600000"})
@AutoConfigureMockMvc
class ReviewerDemoFlowTest {
    @Autowired MockMvc mvc;
    @Autowired KakaoAccountService accounts;
    @Autowired RoomRepository rooms;
    @Autowired MemberRepository members;
    @Autowired ScrollRepository scrolls;
    @Autowired ReviewerDemoSessionRepository demoSessions;

    @Test
    void loggedInReviewerGetsThreeReusableSampleRooms() throws Exception {
        String userId = accounts.findOrCreate(UUID.randomUUID().toString());
        MockHttpSession session = session(userId);
        String response = mvc.perform(post("/api/v1/reviewer-demo/activate").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostRoomId").exists())
                .andExpect(jsonPath("$.data.memberRoomId").exists())
                .andExpect(jsonPath("$.data.completedRoomId").exists())
                .andReturn().getResponse().getContentAsString();

        String hostRoomId = JsonPath.read(response, "$.data.hostRoomId");
        String memberRoomId = JsonPath.read(response, "$.data.memberRoomId");
        String completedRoomId = JsonPath.read(response, "$.data.completedRoomId");
        assertThat(members.findByRoomIdAndUserId(hostRoomId, userId).orElseThrow().getRole()).isEqualTo(MemberRole.HOST);
        assertThat(members.findByRoomIdAndUserId(memberRoomId, userId).orElseThrow().getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(rooms.findById(completedRoomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.GROWING);
        assertThat(scrolls.findTopByRoomIdOrderByVersionDesc(completedRoomId)).isPresent();

        mvc.perform(get("/api/v1/users/me/rooms").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
        mvc.perform(get("/api/v1/rooms/" + completedRoomId + "/scroll").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paragraphs.length()").value(1));

        mvc.perform(post("/api/v1/reviewer-demo/activate").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostRoomId").value(hostRoomId))
                .andExpect(jsonPath("$.data.memberRoomId").value(memberRoomId))
                .andExpect(jsonPath("$.data.completedRoomId").value(completedRoomId));
    }

    @Test
    void olderDemoPhotosAreReplacedWhenTheSeedVersionChanges() throws Exception {
        String userId = accounts.findOrCreate(UUID.randomUUID().toString());
        MockHttpSession session = session(userId);
        String first = mvc.perform(post("/api/v1/reviewer-demo/activate").session(session).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oldHostRoomId = JsonPath.read(first, "$.data.hostRoomId");
        String oldMemberRoomId = JsonPath.read(first, "$.data.memberRoomId");
        String oldCompletedRoomId = JsonPath.read(first, "$.data.completedRoomId");

        demoSessions.deleteById(demoSessions.findByUserId(userId).orElseThrow().getId());
        demoSessions.save(ReviewerDemoSession.create(userId, oldHostRoomId, oldMemberRoomId, oldCompletedRoomId, 1));

        String refreshed = mvc.perform(post("/api/v1/reviewer-demo/activate").session(session).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newHostRoomId = JsonPath.read(refreshed, "$.data.hostRoomId");

        assertThat(newHostRoomId).isNotEqualTo(oldHostRoomId);
        assertThat(rooms.findById(oldHostRoomId)).isEmpty();
        assertThat(rooms.findById(newHostRoomId)).isPresent();
    }

    private static MockHttpSession session(String userId) {
        AccountPrincipal principal = new AccountPrincipal(userId);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
