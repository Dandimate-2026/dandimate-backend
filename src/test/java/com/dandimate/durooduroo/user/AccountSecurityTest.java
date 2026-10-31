package com.dandimate.durooduroo.user;

import com.dandimate.durooduroo.common.auth.AccountPrincipal;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.user.service.KakaoAccountService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:account-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "KAKAO_CLIENT_ID=test-client", "KAKAO_CLIENT_SECRET=test-secret", "KAKAO_REDIRECT_URI=http://localhost/login/oauth2/code/kakao",
    "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000"})
@AutoConfigureMockMvc
class AccountSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired KakaoAccountService accounts;
    @Autowired MemberRepository members;
    private static final String CONSENT="\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";
    private MockHttpSession session(String id) {
        AccountPrincipal p = new AccountPrincipal(id);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new OAuth2AuthenticationToken(p, p.getAuthorities(), "kakao"));
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx); return s;
    }
    private String account() { return accounts.findOrCreate(UUID.randomUUID().toString()); }
    private String create(MockHttpSession s, String key) throws Exception {
        return mvc.perform(post("/api/v1/rooms").session(s).with(csrf()).header("Idempotency-Key",key)
            .contentType("application/json").content("{\"name\":\"여행\",\"meetingDate\":\"2026-10-31\",\"hostNickname\":\"서연\","+CONSENT+"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.memberAccessToken").doesNotExist())
            .andReturn().getResponse().getContentAsString();
    }
    @Test void sameKakaoIdentityReusesAccount() {
        String subject=UUID.randomUUID().toString();
        assertThat(accounts.findOrCreate(subject)).isEqualTo(accounts.findOrCreate(subject));
    }
    @Test void anonymousAndOldBearerCannotAccessAccountOrCreateRoom() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer old-token")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/rooms").with(csrf())).andExpect(status().isUnauthorized());
    }
    @Test void sessionRequiresCsrfAndLogoutInvalidatesIt() throws Exception {
        MockHttpSession s=session(account());
        mvc.perform(get("/api/v1/users/me").session(s)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/rooms").session(s)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/csrf").session(s)).andExpect(jsonPath("$.data.token").isNotEmpty());
        mvc.perform(post("/api/v1/auth/logout").session(s).with(csrf())).andExpect(status().isNoContent());
        assertThat(s.isInvalid()).isTrue();
    }
    @Test void invitationsRemainPublicButJoiningAndRoomAccessUseAccountMembership() throws Exception {
        String host=account(), guest=account(); MockHttpSession hs=session(host), gs=session(guest);
        String created=create(hs,UUID.randomUUID().toString());
        String room=JsonPath.read(created,"$.data.room.id"), invite=JsonPath.read(created,"$.data.inviteToken");
        mvc.perform(get("/api/v1/invites/"+invite)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/rooms/"+room).session(gs)).andExpect(status().isForbidden());
        String path="/api/v1/invites/"+invite+"/join";
        mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
        for(int i=0;i<2;i++) mvc.perform(post(path).session(gs).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content("{\"nickname\":\"민주\","+CONSENT+"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.memberAccessToken").doesNotExist());
        assertThat(members.findAllByUserId(guest)).hasSize(1);
        mvc.perform(get("/api/v1/rooms/"+room).session(gs)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me/rooms").session(gs)).andExpect(jsonPath("$.data[0].roomId").value(room));
        mvc.perform(delete("/api/v1/rooms/"+room).session(gs).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void idempotencyIsScopedToAccountEvenForIdenticalRoomRequests() throws Exception {
        String a=account(),b=account(),key=UUID.randomUUID().toString();
        MockHttpSession sa=session(a),sb=session(b);
        String first=JsonPath.read(create(sa,key),"$.data.room.id");
        assertThat((String)JsonPath.read(create(sa,key),"$.data.room.id")).isEqualTo(first);
        assertThat((String)JsonPath.read(create(sb,key),"$.data.room.id")).isNotEqualTo(first);
        create(sa,UUID.randomUUID().toString());
        mvc.perform(get("/api/v1/users/me/rooms").session(sa)).andExpect(jsonPath("$.data.length()").value(2));
    }
    @Test void oauthStartsWithStateAndRejectsUnsolicitedCallback() throws Exception {
        mvc.perform(get("/oauth2/authorization/kakao")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location",org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("https://kauth.kakao.com/oauth/authorize"),org.hamcrest.Matchers.containsString("state="))));
        mvc.perform(get("/login/oauth2/code/kakao").param("code","untrusted").param("state","wrong"))
            .andExpect(status().isUnauthorized());
    }

    private String postJson(String path, MockHttpSession session, String body) throws Exception {
        return mvc.perform(post(path).session(session).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType("application/json").content(body)).andExpect(status().is2xxSuccessful())
            .andReturn().getResponse().getContentAsString();
    }
    @Autowired com.dandimate.durooduroo.job.service.JobWorker worker;
    @Test void sessionUsersCanUploadTranscribeConfirmAndGenerateScrollWithSeparateAuthors() throws Exception {
        MockHttpSession host=session(account()), guest=session(account());
        String created=create(host,UUID.randomUUID().toString());
        String room=JsonPath.read(created,"$.data.room.id"), invite=JsonPath.read(created,"$.data.inviteToken");
        String hostMember=JsonPath.read(created,"$.data.member.id");
        String joined=postJson("/api/v1/invites/"+invite+"/join",guest,"{\"nickname\":\"민주\","+CONSENT+"}");
        String guestMember=JsonPath.read(joined,"$.data.member.id");
        String uploaded=postJson("/api/v1/rooms/"+room+"/photos/upload-requests",host,
            "{\"files\":[{\"clientFileId\":\"one\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":3,\"displayOrder\":1}]}");
        String photo=JsonPath.read(uploaded,"$.data.uploads[0].photoId"), url=JsonPath.read(uploaded,"$.data.uploads[0].uploadUrl");
        mvc.perform(put(url.substring("http://localhost".length())).contentType("image/png").content(new byte[]{1,2,3}))
            .andExpect(status().isOk());
        postJson("/api/v1/photos/"+photo+"/complete",host,"{}");
        for(int i=0;i<2;i++) {
            MockHttpSession author=i==0?host:guest;
            String kind=i==0?"FIRST":"ADDON", member=i==0?hostMember:guestMember;
            byte[] wav=com.dandimate.durooduroo.memory.audio.VoiceSamples.wav(6);
            String draft=mvc.perform(multipart("/api/v1/photos/"+photo+"/transcriptions")
                .file(new org.springframework.mock.web.MockMultipartFile("file","voice.wav","audio/wav",wav))
                .param("kind",kind).session(author).with(csrf())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
            String draftId=JsonPath.read(draft,"$.data.transcriptionId");
            String entry=postJson("/api/v1/photos/"+photo+"/entries",author,
                "{\"kind\":\""+kind+"\",\"inputMode\":\"VOICE\",\"transcriptionId\":\""+draftId+"\",\"transcript\":\"경주에서 함께 웃었던 날이야\"}");
            assertThat((String)JsonPath.read(entry,"$.data.entry.member.id")).isEqualTo(member);
            String audio=JsonPath.read(entry,"$.data.entry.audioUrl");
            mvc.perform(get(audio.substring("http://localhost".length()))).andExpect(status().isOk());
        }
        String closed=postJson("/api/v1/rooms/"+room+"/close",host,"{\"mode\":\"DEMO\"}");
        String job=JsonPath.read(closed,"$.data.job.id");
        for(int i=0;i<5;i++) worker.poll();
        mvc.perform(get("/api/v1/jobs/"+job).session(host)).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        String scroll=mvc.perform(get("/api/v1/rooms/"+room+"/scroll").session(guest)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(scroll).contains(hostMember,guestMember);
    }

    @Test void csrfEndpointTokenWorksWithBrowserStyleRequestsAndCorsRestrictsOrigins() throws Exception {
        MockHttpSession s=session(account());
        String csrfBody=mvc.perform(get("/api/v1/auth/csrf").session(s)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String token=JsonPath.read(csrfBody,"$.data.token");
        mvc.perform(post("/api/v1/rooms").session(s).header("X-CSRF-TOKEN",token)
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
            .content("{\"name\":\"여행\",\"meetingDate\":\"2026-10-31\",\"hostNickname\":\"서연\","+CONSENT+"}"))
            .andExpect(status().isCreated());
        mvc.perform(options("/api/v1/rooms").header("Origin","http://localhost:3000")
            .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","X-CSRF-TOKEN,Content-Type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials","true"));
        mvc.perform(options("/api/v1/rooms").header("Origin","https://untrusted.example")
            .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
    }
}
