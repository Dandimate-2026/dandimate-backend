package com.dandimate.durooduroo.payment;

import com.dandimate.durooduroo.common.auth.AccountPrincipal;
import com.dandimate.durooduroo.common.exception.*;
import com.dandimate.durooduroo.member.domain.*;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.domain.*;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.user.service.KakaoAccountService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:payments;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000"})
@AutoConfigureMockMvc
class PaymentFlowTest {
    @Autowired MockMvc mvc;
    @Autowired KakaoAccountService accounts;
    @Autowired RoomRepository rooms;
    @Autowired MemberRepository members;
    @Autowired PaymentOrderRepository orders;
    @MockitoBean PaymentGateway gateway;
    @BeforeEach void setup() { when(gateway.configured()).thenReturn(true); }
    private record Actor(String id, MockHttpSession session) {}
    private Actor actor() {
        String id=accounts.findOrCreate(UUID.randomUUID().toString());
        var principal=new AccountPrincipal(id);
        var ctx=SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao"));
        var session=new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        return new Actor(id,session);
    }
    private MockHttpServletRequestBuilder request(String path, Actor a, String body) {
        return post("/api/v1"+path).session(a.session()).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType("application/json").content(body);
    }
    @Autowired com.dandimate.durooduroo.chat.quota.ChatQuotaService quota;
    @Test void creditPacksApproveExactlyOnceForBuyer() throws Exception {
        Actor buyer=actor(),other=actor();int total=0;
        String[] packs={"light","basic","plenty"};int[] amounts={900,1900,2900},counts={10,30,50};
        for(int i=0;i<packs.length;i++) {
            String requestBody=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("packId",packs[i]));
            String id=JsonPath.read(mvc.perform(request("/payments/credit-orders",buyer,requestBody)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(amounts[i])).andExpect(jsonPath("$.data.creditAmount").value(counts[i]))
                .andReturn().getResponse().getContentAsString(),"$.data.orderId");
            String confirm=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("orderId",id,"paymentKey","credit-"+id,"amount",amounts[i]));
            mvc.perform(request("/payments/confirm",other,confirm)).andExpect(status().isNotFound());
            for(int n=0;n<2;n++)mvc.perform(request("/payments/confirm",buyer,confirm)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DONE"));
            total+=counts[i];assertThat(quota.get(buyer.id()).credits()).isEqualTo(total);
            verify(gateway,times(1)).approve("credit-"+id,id,amounts[i]);
        }
        assertThat(quota.get(other.id()).credits()).isZero();
    }

    private String body(String plan) {
        return "{\"name\":\"여행\",\"meetingDate\":\"2026-11-04\",\"hostNickname\":\"방장\",\"capacity\":4,\"planType\":\""+plan+"\",\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}}";
    }
    private String order(Actor a) throws Exception {
        return JsonPath.read(mvc.perform(request("/payments/room-orders", a, body("LITE")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.amount").value(2900))
            .andReturn().getResponse().getContentAsString(), "$.data.orderId");
    }
    private MockHttpServletRequestBuilder confirm(Actor a,String order,String key,int amount) {
        return request("/payments/confirm",a,"{\"orderId\":\""+order+"\",\"paymentKey\":\""+key+"\",\"amount\":"+amount+"}");
    }
    @Test void paidCreationRequiresOwnerAmountApprovalAndReplaysOnlyOnce() throws Exception {
        Actor a=actor(); String id=order(a), key=UUID.randomUUID().toString();
        mvc.perform(confirm(actor(),id,key,2900)).andExpect(status().isNotFound());
        mvc.perform(confirm(a,id,key,1)).andExpect(status().isBadRequest());
        verify(gateway,never()).approve(anyString(),anyString(),anyInt());
        String result=mvc.perform(confirm(a,id,key,2900)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DONE"))
            .andReturn().getResponse().getContentAsString();
        String roomId=JsonPath.read(result,"$.data.roomId");
        mvc.perform(confirm(a,id,key,2900)).andExpect(status().isOk()).andExpect(jsonPath("$.data.roomId").value(roomId));
        verify(gateway,times(1)).approve(key,id,2900);
        assertThat(rooms.findById(roomId).orElseThrow().getPlanType()).isEqualTo(RoomPlan.LITE);
        assertThat(members.findByRoomIdAndUserId(roomId,a.id()).orElseThrow().getRole()).isEqualTo(MemberRole.HOST);
        mvc.perform(get("/api/v1/payments/orders/"+id).session(actor().session())).andExpect(status().isNotFound());
    }
    @Test void upgradeChargesDifferencePreservesUsageAndRejectsDowngradesAndStaleOrders() throws Exception {
        Actor a=actor(); Room room=rooms.save(Room.create("upgrade",java.time.LocalDate.now(),4,RoomPlan.LITE));
        room.reserveVoice(1000); room.settleVoice(1000,true); rooms.save(room);
        members.save(Member.host(room.getId(),"host",a.id()));
        Actor guest=actor(); members.save(Member.join(room.getId(),"guest",FlowerCode.FORSYTHIA,guest.id()));
        String path="/rooms/"+room.getId()+"/plan-orders";
        mvc.perform(request(path,guest,"{\"planType\":\"PRO\"}")).andExpect(status().isForbidden());
        for(String plan:java.util.List.of("FREE","LITE")) mvc.perform(request(path,a,"{\"planType\":\""+plan+"\"}")).andExpect(status().isBadRequest());
        String id=JsonPath.read(mvc.perform(request(path,a,"{\"planType\":\"PRO\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.amount").value(3000)).andReturn().getResponse().getContentAsString(),"$.data.orderId");
        String stale=JsonPath.read(mvc.perform(request(path,a,"{\"planType\":\"PRO\"}")).andReturn().getResponse().getContentAsString(),"$.data.orderId");
        mvc.perform(confirm(a,id,UUID.randomUUID().toString(),3000)).andExpect(status().isOk());
        mvc.perform(confirm(a,stale,UUID.randomUUID().toString(),3000)).andExpect(status().isConflict());
        Room saved=rooms.findById(room.getId()).orElseThrow();
        assertThat(saved.getPlanType()).isEqualTo(RoomPlan.PRO);
        assertThat(saved.getVoiceUsedMs()).isEqualTo(1000);
        mvc.perform(request("/rooms/"+room.getId()+"/plan-requests",guest,"{\"reason\":\"PHOTO\"}")).andExpect(status().isBadRequest());
    }
    @Test void uncertainApprovalKeepsReservationAndRetryRecovers() throws Exception {
        Actor a=actor(); Room room=rooms.save(Room.create("retry")); members.save(Member.host(room.getId(),"host",a.id()));
        Actor b=actor(); Member target=members.save(Member.join(room.getId(),"guest",FlowerCode.FORSYTHIA,b.id()));
        String id=JsonPath.read(mvc.perform(request("/rooms/"+room.getId()+"/plan-orders",a,"{\"planType\":\"LITE\"}"))
            .andReturn().getResponse().getContentAsString(),"$.data.orderId");
        String key=UUID.randomUUID().toString();
        doThrow(new BusinessException(ErrorCode.PAYMENT_UNCERTAIN,"timeout")).doNothing().when(gateway).approve(key,id,2900);
        mvc.perform(confirm(a,id,key,2900)).andExpect(status().isServiceUnavailable());
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo("PROCESSING");
        assertThat(rooms.findById(room.getId()).orElseThrow().getPlanType()).isEqualTo(RoomPlan.FREE);
        mvc.perform(request("/rooms/"+room.getId()+"/host-transfer",a,"{\"memberId\":\""+target.getId()+"\"}")).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/rooms/"+room.getId()).session(a.session()).with(csrf())).andExpect(status().isConflict());
        mvc.perform(confirm(a,id,key,2900)).andExpect(status().isOk());
        mvc.perform(request("/rooms/"+room.getId()+"/host-transfer",a,"{\"memberId\":\""+target.getId()+"\"}")).andExpect(status().isOk());
        assertThat(members.findByRoomIdAndUserId(room.getId(),a.id()).orElseThrow().getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(members.findById(target.getId()).orElseThrow().getRole()).isEqualTo(MemberRole.HOST);
        mvc.perform(request("/rooms/"+room.getId()+"/host-transfer",a,"{\"memberId\":\""+target.getId()+"\"}")).andExpect(status().isForbidden());
    }
    @Test void rejectedApprovalDoesNotGrantAndReleasesRoomReservation() throws Exception {
        Actor a=actor(); String id=order(a),key=UUID.randomUUID().toString();
        doThrow(new BusinessException(ErrorCode.PAYMENT_REJECTED,"rejected")).when(gateway).approve(key,id,2900);
        mvc.perform(confirm(a,id,key,2900)).andExpect(status().isUnprocessableEntity());
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo("FAILED");
        assertThat(orders.findById(id).orElseThrow().getRoomId()).isNull();
    }
    @Test void concurrentConfirmGrantsExactlyOneRoom() throws Exception {
        Actor a=actor(); String id=order(a), key=UUID.randomUUID().toString();
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        try (var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> attempt=() -> {
                barrier.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(confirm(a,id,key,2900)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            };
            var first=executor.submit(attempt); var second=executor.submit(attempt);
            String firstRoom=JsonPath.read(first.get(10,java.util.concurrent.TimeUnit.SECONDS),"$.data.roomId");
            String secondRoom=JsonPath.read(second.get(10,java.util.concurrent.TimeUnit.SECONDS),"$.data.roomId");
            assertThat(firstRoom).isEqualTo(secondRoom);
        }
        verify(gateway,times(1)).approve(key,id,2900);
    }

}
