package com.dandimate.durooduroo.reviewerdemo;

import com.dandimate.durooduroo.common.auth.AccountPrincipal;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.askback.repository.AskbackRepository;
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
    @Autowired AskbackRepository askbacks;

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
        assertThat(askbacks.findAllByRoomIdAndStatus(hostRoomId, "PENDING"))
                .anyMatch(askback -> askback.getTargetMemberId().equals(members.findByRoomIdAndUserId(hostRoomId, userId).orElseThrow().getId()));

        mvc.perform(get("/api/v1/users/me/rooms").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
        mvc.perform(get("/api/v1/rooms/" + completedRoomId + "/scroll").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paragraphs.length()").value(3));

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

    @Autowired com.dandimate.durooduroo.chat.repository.ChatMessageRepository chatMessages;
    @Autowired com.dandimate.durooduroo.chat.service.ChatConversationService conversations;
    @Autowired com.dandimate.durooduroo.chat.service.ChatRecordAccess access;
    @Autowired com.dandimate.durooduroo.photo.repository.PhotoRepository photos;
    @Autowired com.dandimate.durooduroo.memory.repository.MemoryEntryRepository entries;
    @Autowired com.dandimate.durooduroo.embedding.service.EmbeddingIndexService embeddings;
    @Autowired com.dandimate.durooduroo.embedding.repository.MemoryEmbeddingRepository vectors;
    @Autowired com.dandimate.durooduroo.job.repository.AiJobRepository jobs;
    @Autowired com.dandimate.durooduroo.reviewerdemo.service.ReviewerDemoService demos;

    @Test
    void toggleRemovesDemoAndDerivedDataButPreservesRealSameCityRecords() throws Exception {
        String user=accounts.findOrCreate(UUID.randomUUID().toString());
        var browser=session(user);
        var real=rooms.save(com.dandimate.durooduroo.room.domain.Room.create("실제 경주",java.time.LocalDate.now(),4,com.dandimate.durooduroo.room.domain.RoomPlan.FREE));
        real.summarizeScroll("실제 경주 기록만 있는 요약");rooms.save(real);
        var host=members.save(com.dandimate.durooduroo.member.domain.Member.host(real.getId(),"나",user));
        var photo=com.dandimate.durooduroo.photo.domain.Photo.pending(UUID.randomUUID().toString(),real.getId(),host.getId(),"real.jpeg","real.jpeg","image/jpeg",0);
        photo.complete("real-hash");photo.locate("첨성대","경주","경주",35.8,129.2);photo.publish();photos.save(photo);
        var realEntry=entries.save(com.dandimate.durooduroo.memory.domain.MemoryEntry.confirm(real.getId(),photo.getId(),host.getId(),com.dandimate.durooduroo.memory.domain.EntryKind.FIRST,"TEXT",null,null,"실제 여행 기록"));
        var original=chatMessages.save(com.dandimate.durooduroo.chat.domain.ChatMessage.of(user,"same-conversation","ASSISTANT","실제 기록 답변"));
        mvc.perform(get("/api/v1/reviewer-demo").session(browser)).andExpect(jsonPath("$.data.enabled").value(false));
        String body=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/reviewer-demo").session(browser).with(csrf()).contentType("application/json").content("{\"enabled\":true}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.rooms.length()").value(3)).andReturn().getResponse().getContentAsString();
        String demoHost=JsonPath.read(body,"$.data.hostRoomId");
        String scope=demoSessions.findByUserId(user).orElseThrow().getId();
        var mixed=com.dandimate.durooduroo.chat.domain.ChatMessage.of(user,"same-conversation","ASSISTANT","실제+데모 경주 답변");mixed.setDemoScope(scope);chatMessages.save(mixed);
        assertThat(conversations.history(user,"same-conversation")).extracting(com.dandimate.durooduroo.chat.domain.ChatMessage::getContent).containsExactly("실제+데모 경주 답변");
        var demoEntry=entries.findAllByPhotoId(photos.findAllByRoomId(demoHost).getFirst().getId()).getFirst();embeddings.enqueue(demoEntry);
        assertThat(vectors.existsById(demoEntry.getId())).isTrue();
        String other=accounts.findOrCreate(UUID.randomUUID().toString());var otherDemo=demos.activate(other);
        assertThat(demos.activate(user).hostRoomId()).isEqualTo(demoHost);
        mvc.perform(get("/api/v1/users/me/map").session(browser)).andExpect(status().isOk()).andExpect(jsonPath("$.data.cities.length()").value(3));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/reviewer-demo").session(browser).with(csrf()).contentType("application/json").content("{\"enabled\":false}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false)).andExpect(jsonPath("$.data.rooms.length()").value(0));
        assertThat(rooms.existsById(demoHost)).isFalse();assertThat(vectors.existsById(demoEntry.getId())).isFalse();
        assertThat(chatMessages.existsById(mixed.getId())).isFalse();assertThat(chatMessages.existsById(original.getId())).isTrue();
        assertThat(access.load(user,false).keySet()).containsExactly(realEntry.getId());
        assertThat(conversations.history(user,"same-conversation")).extracting(com.dandimate.durooduroo.chat.domain.ChatMessage::getContent).containsExactly("실제 기록 답변");
        assertThat(rooms.existsById(otherDemo.hostRoomId())).isTrue();
        assertThat(rooms.findById(real.getId()).orElseThrow().getScrollSummary()).isEqualTo("실제 경주 기록만 있는 요약");
        mvc.perform(get("/api/v1/users/me/map").session(browser)).andExpect(status().isOk()).andExpect(jsonPath("$.data.cities.length()").value(1))
          .andExpect(jsonPath("$.data.cities[0].summary").value("실제 경주 기록만 있는 요약"));
        assertThat(jobs.findAll()).anyMatch(j->("reviewer-demo/"+demoHost+"/").equals(j.getTargetId()));
        assertThat(demos.setEnabled(user,false).enabled()).isFalse();
        assertThat(demos.setEnabled(user,true).hostRoomId()).isNotEqualTo(demoHost);
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
