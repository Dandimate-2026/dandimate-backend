package com.dandimate.durooduroo.room;
import com.dandimate.durooduroo.common.auth.AccountPrincipal;
import com.dandimate.durooduroo.member.domain.*;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.payment.PaymentGateway;
import com.dandimate.durooduroo.user.service.KakaoAccountService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:roomdetails;MODE=MySQL;DB_CLOSE_DELAY=-1", "storage.local-root=build/room-cover-test-uploads", "app.jobs.poll-interval-ms=3600000"})
@AutoConfigureMockMvc
class RoomDetailsFlowTest {
 @Autowired MockMvc mvc;
 @Autowired KakaoAccountService accounts;
 @Autowired RoomRepository rooms;
 @Autowired MemberRepository members;
 @Autowired PhotoRepository photos;
 @MockitoBean PaymentGateway gateway;
 private record Actor(String id,MockHttpSession session){}
 private Actor actor(){
  String id=accounts.findOrCreate(UUID.randomUUID().toString());var p=new AccountPrincipal(id);
  var ctx=SecurityContextHolder.createEmptyContext();ctx.setAuthentication(new OAuth2AuthenticationToken(p,p.getAuthorities(),"kakao"));
  var session=new MockHttpSession();session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,ctx);return new Actor(id,session);
 }
 @BeforeEach void setup(){when(gateway.configured()).thenReturn(true);}
 private static final String PLACE="{\"placeName\":\"경주역\",\"address\":\"경주시\",\"latitude\":35.84,\"longitude\":129.21}";
 private String body(String plan,String cover){return "{\"name\":\"여행\",\"meetingDate\":\"2026-11-06\",\"hostNickname\":\"방장\",\"planType\":\""+plan+"\",\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true},\"place\":"+PLACE+",\"coverPhoto\":{\"id\":\""+cover+"\"}}";}
 private String upload(Actor a)throws Exception{
  var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
  var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
  return JsonPath.read(mvc.perform(multipart("/api/v1/room-covers").file(new MockMultipartFile("file","cover.png","image/png",bytes.toByteArray())).session(a.session()).with(csrf()))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.data.imageUrl").exists()).andReturn().getResponse().getContentAsString(),"$.data.id");
 }
 private String create(Actor a,String cover)throws Exception{
  return JsonPath.read(mvc.perform(post("/api/v1/rooms").session(a.session()).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body("FREE",cover)))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.data.room.place.placeName").value("경주역")).andExpect(jsonPath("$.data.room.coverPhoto.id").value(cover)).andReturn().getResponse().getContentAsString(),"$.data.room.id");
 }
 @Test void createHomeListAndPartialPatchPreserveOrClear()throws Exception{
  var a=actor();String cover=upload(a), room=create(a,cover);
  assertThat(photos.countByRoomId(room)).isZero();
  mvc.perform(get("/api/v1/rooms/"+room).session(a.session())).andExpect(status().isOk()).andExpect(jsonPath("$.data.room.place.latitude").value(35.84)).andExpect(jsonPath("$.data.room.coverPhoto.id").value(cover));
  mvc.perform(get("/api/v1/users/me/rooms").session(a.session())).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].coverPhoto.id").value(cover)).andExpect(jsonPath("$.data[0].place.placeName").value("경주역"));
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.room.coverPhoto.id").value(cover));
  String replacement=upload(a);
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"place\":"+PLACE.replace("경주역","다른 장소")+",\"coverPhoto\":{\"id\":\""+replacement+"\"}}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.room.place.placeName").value("다른 장소")).andExpect(jsonPath("$.data.room.coverPhoto.id").value(replacement));
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"place\":null,\"coverPhoto\":null}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.room.place").isEmpty()).andExpect(jsonPath("$.data.room.coverPhoto").isEmpty());
  assertThat(rooms.findById(room).orElseThrow().getCoverPhotoId()).isNull();
 }
 @Test void ownershipMembershipAndHostOnlyPatch()throws Exception{
  var a=actor();var b=actor();String cover=upload(a),room=create(a,cover),url="/api/v1/room-covers/"+cover+"/image";
  mvc.perform(get(url)).andExpect(status().isUnauthorized());
  mvc.perform(get(url).session(b.session())).andExpect(status().isForbidden());
  mvc.perform(get(url).session(a.session())).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  mvc.perform(post("/api/v1/rooms").session(b.session()).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body("FREE",cover))).andExpect(status().isForbidden());
  var member=members.save(Member.join(room,"친구",FlowerCode.FORSYTHIA,b.id()));
  mvc.perform(get(url).session(b.session())).andExpect(status().isOk());
  mvc.perform(patch("/api/v1/rooms/"+room).session(b.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"place\":null}")).andExpect(status().isForbidden());
  member.leave();members.save(member);
  mvc.perform(get(url).session(b.session())).andExpect(status().isForbidden());
 }
 @Test void paymentOrderPreservesDetailsAndRejectsOtherUsersCoverBeforeApproval()throws Exception{
  var a=actor();String cover=upload(a);
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var request=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(body("LITE",cover));
  request.remove("coverPhoto");request.put("coverImageUrl","/api/v1/room-covers/"+cover+"/image").put("capacity",1);
  String paidBody=request.toString();
  mvc.perform(post("/api/v1/payments/room-orders").session(actor().session()).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(paidBody)).andExpect(status().isForbidden());
  verify(gateway,never()).approve(anyString(),anyString(),anyInt());
  String order=JsonPath.read(mvc.perform(post("/api/v1/payments/room-orders").session(a.session()).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(paidBody))
   .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.data.orderId");
  String room=JsonPath.read(mvc.perform(post("/api/v1/payments/confirm").session(a.session()).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\""+order+"\",\"paymentKey\":\""+UUID.randomUUID()+"\",\"amount\":2900}"))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"$.data.roomId");
  var saved=rooms.findById(room).orElseThrow();assertThat(saved.getCapacity()).isEqualTo(1);assertThat(saved.getCoverPhotoId()).isEqualTo(cover);assertThat(saved.getPlace().placeName()).isEqualTo("경주역");
 }
 @Test void validatesImageAndPlace()throws Exception{
  var a=actor();
  mvc.perform(multipart("/api/v1/room-covers").file(new MockMultipartFile("file","bad.png","image/png",new byte[]{1,2,3})).session(a.session()).with(csrf())).andExpect(status().isUnsupportedMediaType());
  mvc.perform(multipart("/api/v1/room-covers").file(new MockMultipartFile("file","big.png","image/png",new byte[2_000_001])).session(a.session()).with(csrf())).andExpect(status().isPayloadTooLarge());
  String cover=upload(a),room=create(a,cover);
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"place\":"+PLACE.replace("35.84","91")+"}"))
   .andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/rooms").session(a.session()).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body("FREE",cover).replace("129.21","181"))).andExpect(status().isBadRequest());
  assertThat(rooms.findById(room).orElseThrow().getPlace().latitude()).isEqualTo(35.84);
 }

 @Test void capacityUsesActiveMembersAndBlocksJoiningFullRoom() throws Exception {
  var a=actor();var b=actor();String room=create(a,upload(a));
  var member=members.save(Member.join(room,"친구",FlowerCode.FORSYTHIA,b.id()));
  mvc.perform(patch("/api/v1/rooms/"+room).session(b.session()).with(csrf()).contentType("application/json").content("{\"capacity\":2}"))
    .andExpect(status().isForbidden());
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"capacity\":1}"))
    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("INVALID_CAPACITY"));
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"capacity\":2}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.data.room.capacity").value(2));
  String token=JsonPath.read(mvc.perform(post("/api/v1/rooms/"+room+"/invite-token/rotate").session(a.session()).with(csrf()))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"$.data.inviteToken");
  mvc.perform(get("/api/v1/invites/"+token)).andExpect(jsonPath("$.data.canJoin").value(false));
  mvc.perform(post("/api/v1/invites/"+token+"/join").session(actor().session()).with(csrf())
    .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
    .content("{\"nickname\":\"세번째\",\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}}"))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROOM_FULL"));
  member.leave();members.save(member);
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"capacity\":1}"))
    .andExpect(status().isOk());
  for(int invalid:new int[]{0,9})
   mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"capacity\":"+invalid+"}"))
    .andExpect(status().isUnprocessableEntity());
  var saved=rooms.findById(room).orElseThrow();saved.startCirculating();rooms.save(saved);
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"capacity\":3}"))
    .andExpect(status().isOk());
  saved=rooms.findById(room).orElseThrow();saved.startWeaving();rooms.save(saved);
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"capacity\":4}"))
    .andExpect(status().isConflict());
  saved.completeWeaving();rooms.save(saved);
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"capacity\":4}"))
    .andExpect(status().isConflict());
 }

 @Test void canonicalCoverUrlAndSoloRoomAndDeadlineMetadata() throws Exception {
  var a=actor();String cover=upload(a),url="/api/v1/room-covers/"+cover+"/image";
  var json=new com.fasterxml.jackson.databind.ObjectMapper();
  var body=json.readTree(body("FREE",cover));((com.fasterxml.jackson.databind.node.ObjectNode)body).remove("coverPhoto");
  ((com.fasterxml.jackson.databind.node.ObjectNode)body).put("coverImageUrl",url).put("capacity",1);
  String room=JsonPath.read(mvc.perform(post("/api/v1/rooms").session(a.session()).with(csrf())
    .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(body.toString()))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.room.capacity").value(1))
    .andExpect(jsonPath("$.data.room.coverImageUrl").value(url)).andReturn().getResponse().getContentAsString(),"$.data.room.id");
  mvc.perform(get("/api/v1/rooms/"+room).session(a.session())).andExpect(jsonPath("$.data.room.createdAt").exists())
    .andExpect(jsonPath("$.data.room.deadlineChangedByHost").value(false));
  var deadline=rooms.findById(room).orElseThrow().getCreatedAt().plusHours(30).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"deadlineAt\":\""+deadline+"\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.data.room.deadlineChangedByHost").value(true));
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"deadlineAt\":\""+deadline.plusHours(1)+"\"}"))
    .andExpect(status().isConflict());
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"coverImageUrl\":null}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.data.room.coverImageUrl").isEmpty());
  mvc.perform(patch("/api/v1/rooms/"+room).session(a.session()).with(csrf()).contentType("application/json").content("{\"coverImageUrl\":\"https://evil.test/photo\"}"))
    .andExpect(status().isBadRequest());
 }
}
