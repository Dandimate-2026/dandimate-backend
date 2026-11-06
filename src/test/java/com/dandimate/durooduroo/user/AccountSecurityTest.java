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
            .contentType("application/json").content("{\"name\":\"여행\",\"meetingDate\":\"2026-10-31\",\"planType\":\"FREE\",\"hostNickname\":\"서연\","+CONSENT+"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.memberAccessToken").doesNotExist())
            .andReturn().getResponse().getContentAsString();
    }
    @Test void onlyHostCanKickAndFixedInviteCannotRejoin() throws Exception {
        String hostId=account(),guestId=account();var host=session(hostId);var guest=session(guestId);var outsider=session(account());
        String created=create(host,UUID.randomUUID().toString());
        String room=JsonPath.read(created,"$.data.room.id"),invite=JsonPath.read(created,"$.data.inviteToken");
        String joinBody=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("nickname","친구","consent",java.util.Map.of("version","v1","terms",true,"mediaProcessing",true,"externalAiProcessing",true)));
        postJson("/api/v1/invites/"+invite+"/join",guest,joinBody);
        String target=members.findByRoomIdAndUserId(room,guestId).orElseThrow().getId();
        String hostMember=members.findByRoomIdAndUserId(room,hostId).orElseThrow().getId();
        String path="/api/v1/rooms/"+room+"/members/"+target;
        mvc.perform(delete(path).session(guest).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(path).session(outsider).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/rooms/"+room+"/members/"+hostMember).session(host).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(path).session(host).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(path).session(host).with(csrf())).andExpect(status().isNoContent());
        assertThat(members.findById(target).orElseThrow().isKicked()).isTrue();
        mvc.perform(get("/api/v1/rooms/"+room).session(guest)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/invites/"+invite).session(guest)).andExpect(status().isOk()).andExpect(jsonPath("$.data.canJoin").value(false));
        mvc.perform(post("/api/v1/invites/"+invite+"/join").session(guest).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(joinBody)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/rooms/"+room+"/invite").session(session(hostId))).andExpect(status().isOk()).andExpect(jsonPath("$.data.inviteToken").value(invite));
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
    @Test void planCatalogIsPublicButDoesNotAuthenticateOrEnableUnpaidRooms() throws Exception {
        mvc.perform(get("/api/v1/plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[*].planType").value(org.hamcrest.Matchers.contains("FREE", "LITE", "PRO")))
            .andExpect(jsonPath("$.data[*].price").value(org.hamcrest.Matchers.contains(0, 2900, 5900)))
            .andExpect(jsonPath("$.data[*].currency").value(org.hamcrest.Matchers.contains("KRW", "KRW", "KRW")))
            .andExpect(jsonPath("$.data[*].billingUnit").value(org.hamcrest.Matchers.contains("ROOM", "ROOM", "ROOM")))
            .andExpect(jsonPath("$.data[*].maxCapacity").value(org.hamcrest.Matchers.contains(8, 8, 8)))
            .andExpect(jsonPath("$.data[*].photosPerMember").value(org.hamcrest.Matchers.contains(3, 6, 10)))
            .andExpect(jsonPath("$.data[*].photosPerRoom").value(org.hamcrest.Matchers.contains(24, 48, 80)))
            .andExpect(jsonPath("$.data[*].firstMaxSeconds").value(org.hamcrest.Matchers.contains(30, 60, 120)))
            .andExpect(jsonPath("$.data[*].otherMaxSeconds").value(org.hamcrest.Matchers.contains(20, 30, 60)))
            .andExpect(jsonPath("$.data[*].voiceLimitMs").value(org.hamcrest.Matchers.contains(900000, 2400000, 5400000)))
            .andExpect(jsonPath("$.data[*].regenerationLimit").value(org.hamcrest.Matchers.contains(3, 6, 12)))
            .andExpect(jsonPath("$.data[0].roomCreationAvailable").value(true))
            .andExpect(jsonPath("$.data[0].paymentRequired").value(false))
            .andExpect(jsonPath("$.data[1].roomCreationAvailable").value(false))
            .andExpect(jsonPath("$.data[2].roomCreationAvailable").value(false))
            .andExpect(jsonPath("$.data[1].unavailableReason").value("PAYMENT_NOT_CONFIGURED"))
            .andExpect(jsonPath("$.data[2].paymentRequired").value(true));
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        MockHttpSession loggedIn = session(account());
        for (String plan : java.util.List.of("LITE", "PRO")) {
            mvc.perform(post("/api/v1/rooms").session(loggedIn).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"name\":\"플랜 테스트\",\"meetingDate\":\"2026-11-03\",\"hostNickname\":\"서연\",\"planType\":\""+plan+"\","+CONSENT+"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_CONFIGURED"));
        }
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
    @Test void invitePreviewIdentifiesCurrentAccountWithoutJoiningOrChangingRoles() throws Exception {
        String hostId=account(), guestId=account();
        MockHttpSession host=session(hostId), guest=session(guestId), outsider=session(account());
        String created=create(host,UUID.randomUUID().toString());
        String room=JsonPath.read(created,"$.data.room.id"), invite=JsonPath.read(created,"$.data.inviteToken");
        String preview="/api/v1/invites/"+invite;
        mvc.perform(get(preview)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roomId").value(room))
            .andExpect(jsonPath("$.data.alreadyJoined").value(false));
        mvc.perform(get(preview).session(host)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alreadyJoined").value(true));
        mvc.perform(get(preview).session(guest)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alreadyJoined").value(false));
        assertThat(members.countByRoomId(room)).isEqualTo(1);
        postJson(preview+"/join",guest,"{\"nickname\":\"민주\","+CONSENT+"}");
        mvc.perform(get(preview).session(guest)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roomId").value(room))
            .andExpect(jsonPath("$.data.alreadyJoined").value(true));
        mvc.perform(get(preview).session(outsider)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alreadyJoined").value(false));
        mvc.perform(get(preview)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alreadyJoined").value(false));
        mvc.perform(get(preview).session(session(hostId))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alreadyJoined").value(true));
        mvc.perform(get("/api/v1/rooms/"+room).session(host)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.me.role").value("HOST"));
        mvc.perform(get("/api/v1/rooms/"+room).session(guest)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.me.role").value("MEMBER"));
        mvc.perform(get("/api/v1/rooms/"+room).session(outsider)).andExpect(status().isForbidden());
        assertThat(members.countByRoomId(room)).isEqualTo(2);
    }
    @Test void existingParticipantCanReenterFullRoomUsingPreviewRoomId() throws Exception {
        MockHttpSession host=session(account()), outsider=session(account());
        String created=postJson("/api/v1/rooms",host,
            "{\"name\":\"정원 테스트\",\"meetingDate\":\"2026-11-04\",\"hostNickname\":\"서연\",\"capacity\":1,\"planType\":\"FREE\","+CONSENT+"}");
        String room=JsonPath.read(created,"$.data.room.id"), invite=JsonPath.read(created,"$.data.inviteToken");
        mvc.perform(get("/api/v1/invites/"+invite).session(host)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roomId").value(room))
            .andExpect(jsonPath("$.data.alreadyJoined").value(true))
            .andExpect(jsonPath("$.data.canJoin").value(false));
        mvc.perform(get("/api/v1/rooms/"+room).session(host)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/invites/"+invite).session(outsider)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alreadyJoined").value(false))
            .andExpect(jsonPath("$.data.canJoin").value(false));
        String rotated=mvc.perform(get("/api/v1/rooms/"+room+"/invite").session(host)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newInvite=JsonPath.read(rotated,"$.data.inviteToken");
        mvc.perform(get("/api/v1/invites/"+invite).session(host)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/invites/"+newInvite).session(host)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alreadyJoined").value(true));
    }

    @Test void nicknameChangesOnlyCurrentMemberAndRejectsDuplicatesAndInvalidNames() throws Exception {
        MockHttpSession host=session(account()), guest=session(account()), outsider=session(account());
        String created=create(host,UUID.randomUUID().toString());
        String room=JsonPath.read(created,"$.data.room.id"), invite=JsonPath.read(created,"$.data.inviteToken");
        postJson("/api/v1/invites/"+invite+"/join",guest,"{\"nickname\":\"민주\","+CONSENT+"}");
        String path="/api/v1/rooms/"+room+"/members/me";
        String response=mvc.perform(patch(path).session(guest).with(csrf()).contentType("application/json")
            .content("{\"nickname\":\" 지수 \"}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname").value("지수"))
            .andExpect(jsonPath("$.data.role").value("MEMBER")).andReturn().getResponse().getContentAsString();
        contract("patch","/rooms/{roomId}/members/me","200",response);
        mvc.perform(get("/api/v1/rooms/"+room).session(host)).andExpect(jsonPath("$.data.me.nickname").value("서연"));
        for (String bad : java.util.List.of("서연"," ","12345678901")) {
            mvc.perform(patch(path).session(guest).with(csrf()).contentType("application/json")
                .content("{\"nickname\":\""+bad+"\"}")).andExpect(status().is(bad.equals("서연")?409:400));
        }
        mvc.perform(patch(path).session(outsider).with(csrf()).contentType("application/json").content("{\"nickname\":\"다른이름\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch(path).session(guest).contentType("application/json").content("{\"nickname\":\"다른이름\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch(path).with(csrf()).contentType("application/json").content("{\"nickname\":\"다른이름\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void planRequestNotifiesOnlyHostAndIsIdempotentWithoutChangingPlan() throws Exception {
        MockHttpSession host=session(account()), guest=session(account()), outsider=session(account());
        String created=create(host,UUID.randomUUID().toString());
        String room=JsonPath.read(created,"$.data.room.id"), invite=JsonPath.read(created,"$.data.inviteToken");
        postJson("/api/v1/invites/"+invite+"/join",guest,"{\"nickname\":\"민주\","+CONSENT+"}");
        String path="/api/v1/rooms/"+room+"/plan-requests", key=UUID.randomUUID().toString(), notificationId=null;
        for (int i=0;i<2;i++) {
            String response=mvc.perform(post(path).session(guest).with(csrf()).header("Idempotency-Key",key)
                .contentType("application/json").content("{\"reason\":\"PHOTO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            contract("post","/rooms/{roomId}/plan-requests","201",response);
            String actual=JsonPath.read(response,"$.data.notificationId");
            if(notificationId!=null) assertThat(actual).isEqualTo(notificationId);
            notificationId=actual;
        }
        mvc.perform(get("/api/v1/rooms/"+room+"/notifications").session(host))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].type").value("PLAN_UPGRADE_REQUESTED"))
            .andExpect(jsonPath("$.data[0].resourceType").value("PHOTO"));
        mvc.perform(get("/api/v1/rooms/"+room+"/notifications").session(guest))
            .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/rooms/"+room).session(host)).andExpect(jsonPath("$.data.room.planType").value("FREE"));
        for(MockHttpSession denied : java.util.List.of(host,outsider)) {
            mvc.perform(post(path).session(denied).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content("{\"reason\":\"VOICE\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(post(path).session(guest).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content("{\"reason\":\"OTHER\"}")).andExpect(status().isBadRequest());
    }

    @Test void leavingReleasesCapacityAndRevokesAccessWhileRejoiningKeepsMemberId() throws Exception {
        String hostId=account(), guestId=account(); MockHttpSession host=session(hostId), guest=session(guestId);
        String created=postJson("/api/v1/rooms",host,
            "{\"name\":\"정원\",\"meetingDate\":\"2026-11-04\",\"hostNickname\":\"서연\",\"capacity\":2,\"planType\":\"FREE\","+CONSENT+"}");
        String room=JsonPath.read(created,"$.data.room.id"), invite=JsonPath.read(created,"$.data.inviteToken");
        String joined=postJson("/api/v1/invites/"+invite+"/join",guest,"{\"nickname\":\"민주\","+CONSENT+"}");
        String member=JsonPath.read(joined,"$.data.member.id");
        String eventToken=postJson("/api/v1/rooms/"+room+"/event-token",guest,"{}");
        String eventsUrl=JsonPath.read(eventToken,"$.data.eventsUrl");
        String path="/api/v1/rooms/"+room+"/members/me";
        mvc.perform(delete(path).session(host).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(path).session(guest)).andExpect(status().isForbidden());
        for(int i=0;i<2;i++) mvc.perform(delete(path).session(guest).with(csrf())).andExpect(status().isNoContent());
        assertThat(members.findById(member).orElseThrow().getLeftAt()).isNotNull();
        mvc.perform(get("/api/v1/users/me/rooms").session(guest)).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/rooms/"+room).session(guest)).andExpect(status().isForbidden());
        mvc.perform(get(eventsUrl.substring("http://localhost".length())).session(guest)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/invites/"+invite).session(guest)).andExpect(jsonPath("$.data.alreadyJoined").value(false))
            .andExpect(jsonPath("$.data.memberCount").value(1)).andExpect(jsonPath("$.data.canJoin").value(true));
        // Nine different former participants must not exhaust the eight flower slots.
        for(int i=0;i<9;i++) {
            MockHttpSession other=session(account());
            postJson("/api/v1/invites/"+invite+"/join",other,"{\"nickname\":\"친구"+i+"\","+CONSENT+"}");
            mvc.perform(post("/api/v1/invites/"+invite+"/join").session(guest).with(csrf())
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"nickname\":\"민주\","+CONSENT+"}")).andExpect(status().isConflict());
            mvc.perform(delete(path).session(other).with(csrf())).andExpect(status().isNoContent());
        }
        String rejoined=postJson("/api/v1/invites/"+invite+"/join",guest,"{\"nickname\":\"민주\","+CONSENT+"}");
        assertThat((String)JsonPath.read(rejoined,"$.data.member.id")).isEqualTo(member);
        assertThat(members.countByRoomIdAndLeftAtIsNull(room)).isEqualTo(2);
        mvc.perform(get("/api/v1/invites/"+invite).session(guest)).andExpect(jsonPath("$.data.alreadyJoined").value(true));
        mvc.perform(get("/api/v1/rooms/"+room).session(host)).andExpect(jsonPath("$.data.me.role").value("HOST"));
        mvc.perform(delete("/api/v1/rooms/"+room).session(guest).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/rooms/"+room).session(host).with(csrf())).andExpect(status().isNoContent());
        assertThat(members.countByRoomId(room)).isZero();
        mvc.perform(get("/api/v1/users/me").session(guest)).andExpect(status().isOk());
    }

    private void contract(String method,String path,String status,String response) throws Exception {
        String pointer="/paths/"+path.replace("~","~0").replace("/","~1")+"/"+method+"/responses/"+status+"/content/application~1json/schema";
        var factory=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012);
        var location=com.networknt.schema.SchemaLocation.of("classpath:static/openapi/public-api.yaml#"+pointer.replace("{","%7B").replace("}","%7D"));
        assertThat(factory.getSchema(location).validate(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response))).isEmpty();
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
                .param("kind",kind).header("Idempotency-Key",UUID.randomUUID().toString()).session(author).with(csrf())).andExpect(status().isCreated())
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
        mvc.perform(delete("/api/v1/rooms/"+room+"/members/me").session(guest).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/rooms/"+room+"/scroll").session(host)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(guestMember)));
        mvc.perform(get("/api/v1/rooms/"+room+"/scroll").session(guest)).andExpect(status().isForbidden());
        String rejoined=postJson("/api/v1/invites/"+invite+"/join",guest,"{\"nickname\":\"민주\","+CONSENT+"}");
        assertThat((String)JsonPath.read(rejoined,"$.data.member.id")).isEqualTo(guestMember);
    }

    @Test void csrfEndpointTokenWorksWithBrowserStyleRequestsAndCorsRestrictsOrigins() throws Exception {
        MockHttpSession s=session(account());
        String csrfBody=mvc.perform(get("/api/v1/auth/csrf").session(s)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String token=JsonPath.read(csrfBody,"$.data.token");
        mvc.perform(post("/api/v1/rooms").session(s).header("X-CSRF-TOKEN",token)
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
            .content("{\"name\":\"여행\",\"meetingDate\":\"2026-10-31\",\"planType\":\"FREE\",\"hostNickname\":\"서연\","+CONSENT+"}"))
            .andExpect(status().isCreated());
        mvc.perform(options("/api/v1/rooms").header("Origin","http://localhost:3000")
            .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","X-CSRF-TOKEN,Content-Type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials","true"));
        mvc.perform(options("/api/v1/rooms").header("Origin","https://untrusted.example")
            .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
    }
}
