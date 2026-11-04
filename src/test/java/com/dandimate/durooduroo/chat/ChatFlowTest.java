package com.dandimate.durooduroo.chat;
import com.dandimate.durooduroo.ai.chat.*;
import com.dandimate.durooduroo.ai.embedding.*;
import com.dandimate.durooduroo.chat.service.*;
import com.dandimate.durooduroo.common.auth.AccountPrincipal;
import com.dandimate.durooduroo.embedding.repository.MemoryEmbeddingRepository;
import com.dandimate.durooduroo.embedding.service.*;
import com.dandimate.durooduroo.member.domain.*;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.*;
import com.dandimate.durooduroo.memory.repository.*;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.user.service.KakaoAccountService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:chat-flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "app.embedding.initial-delay-ms=3600000","app.jobs.poll-interval-ms=3600000","app.jobs.deadline-check-interval-ms=3600000"})
@AutoConfigureMockMvc
class ChatFlowTest {
 @Autowired MockMvc mvc;
 @Autowired ChatService chat;
 @Autowired ChatRecordAccess access;
 @Autowired EmbeddingIndexService worker;
 @Autowired MemoryEmbeddingRepository index;
 @Autowired MemoryEntryRepository entries;
 @Autowired MemberRepository members;
 @Autowired RoomRepository rooms;
 @Autowired PhotoRepository photos;
 @Autowired PhotoPassRepository passes;
 @Autowired KakaoAccountService accounts;
 @Autowired TransactionTemplate tx;
 @MockitoBean OpenAiEmbeddingService embedding;
 @MockitoBean ChatAiService ai;
 String ownerUser,guestUser;
 Member owner,guest;
 Photo photo;
 MemoryEntry first,second;
 @BeforeEach void setup() {
  index.deleteAll();entries.deleteAll();
  when(embedding.model()).thenReturn("text-embedding-3-small");
  when(embedding.embed(anyList())).thenAnswer(inv->new EmbeddingResult("text-embedding-3-small",3,
    ((List<?>)inv.getArgument(0)).stream().map(x->List.of(1.0,0.0,0.0)).toList()));
  ownerUser=accounts.findOrCreate(UUID.randomUUID().toString());guestUser=accounts.findOrCreate(UUID.randomUUID().toString());
  var room=rooms.save(Room.create("경주 여행"));
  owner=members.save(Member.host(room.getId(),"서연",ownerUser));
  guest=members.save(Member.join(room.getId(),"민주",FlowerCode.FORSYTHIA,guestUser));
  photo=Photo.pending(UUID.randomUUID().toString(),room.getId(),owner.getId(),"photo.jpg","local/photo.jpg","image/jpeg",1);
  photo.complete("hash");photo.publish();photos.save(photo);
  first=entries.save(MemoryEntry.confirm(room.getId(),photo.getId(),guest.getId(),EntryKind.ADDON,"TEXT",null,null,"경주에서 함께 웃었어"));
  second=entries.save(MemoryEntry.confirm(room.getId(),photo.getId(),guest.getId(),EntryKind.ADDON,"TEXT",null,null,"밤에는 달을 봤어"));
  worker.enqueue(first);worker.enqueue(second);worker.runNext();worker.runNext();
  when(ai.answer(any())).thenReturn(new ChatResult("민주는 경주에서 함께 웃었다고 했어요.",List.of(first.getId())));
 }
 MockHttpSession session(String user) {
  var p=new AccountPrincipal(user);var ctx=SecurityContextHolder.createEmptyContext();
  ctx.setAuthentication(new OAuth2AuthenticationToken(p,p.getAuthorities(),"kakao"));
  var s=new MockHttpSession();s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,ctx);return s;
 }
 @Test void endpointIncludesRoomPhotoUploaderAndEveryAddonAuthorInOrder() throws Exception {
  mvc.perform(post("/api/v1/chat/messages").session(session(ownerUser)).with(csrf()).contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("message","경주에서 뭐 했지?"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value("민주는 경주에서 함께 웃었다고 했어요."))
   .andExpect(jsonPath("$.data.sources[0].room.roomId").value(photo.getRoomId()))
   .andExpect(jsonPath("$.data.sources[0].photo.uploader.userId").value(ownerUser))
   .andExpect(jsonPath("$.data.sources[0].photo.uploader.nickname").value("서연"))
   .andExpect(jsonPath("$.data.sources[0].entries.length()").value(2))
   .andExpect(jsonPath("$.data.sources[0].entries[0].entryId").value(first.getId()))
   .andExpect(jsonPath("$.data.sources[0].entries[1].entryId").value(second.getId()))
   .andExpect(jsonPath("$.data.sources[0].entries[0].author.userId").value(guestUser));
  verify(ai).answer(argThat(c->c.evidence().stream().allMatch(e->e.authorNickname().equals("민주")&&e.uploaderNickname().equals("서연"))));
 }
 @Test void authenticationAndCsrfRequired() throws Exception {
  mvc.perform(post("/api/v1/chat/messages").with(csrf()).contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("message","hi")))).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/chat/messages").session(session(ownerUser)).contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("message","hi")))).andExpect(status().isForbidden());
 }
 @Test void outsiderAndLockedMemberNeverSendPrivateOriginalsToAi() {
  String outsider=accounts.findOrCreate(UUID.randomUUID().toString());
  assertThat(chat.answer(outsider,"뭐 했지").sources()).isEmpty();
  var locked=members.save(Member.join(photo.getRoomId(),"지수",FlowerCode.ROYAL_AZALEA,outsider));
  assertThat(chat.answer(outsider,"뭐 했지").sources()).isEmpty();
  verifyNoInteractions(ai);
  passes.save(PhotoPass.of(photo.getRoomId(),photo.getId(),locked.getId()));
  assertThat(chat.answer(outsider,"뭐 했지").sources()).hasSize(1);
 }
 @Test void leaveWhileLlmIsRunningDiscardsAnswer() {
  when(ai.answer(any())).thenAnswer(inv->{guest.leave();members.save(guest);return new ChatResult("기억",List.of(first.getId()));});
  assertThatThrownBy(()->chat.answer(guestUser,"뭐 했지")).hasMessageContaining("권한");
 }
 @Test void editDuringLlmDiscardsAnswer() {
  when(ai.answer(any())).thenAnswer(inv->{first.edit("새 원문");entries.save(first);return new ChatResult("기억",List.of(first.getId()));});
  assertThatThrownBy(()->chat.answer(ownerUser,"뭐 했지")).hasMessageContaining("변경");
 }
 @Test void fabricatedCitationIsRejected() {
  when(ai.answer(any())).thenReturn(new ChatResult("기억",List.of(UUID.randomUUID().toString())));
  assertThatThrownBy(()->chat.answer(ownerUser,"뭐 했지")).hasMessageContaining("AI 응답");
 }
 @Test void editResetsIndexAndDeleteCascadesWithoutChangingOriginalStorage() {
  first.edit("새로운 기억");first=entries.save(first);worker.enqueue(first);
  assertThat(index.findById(first.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
  assertThat(entries.findById(first.getId()).orElseThrow().getTranscript()).isEqualTo("새로운 기억");
  worker.runNext();
  assertThat(index.findById(first.getId()).orElseThrow().getSourceHash()).isEqualTo(EmbeddingText.hash("새로운 기억"));
  entries.deleteById(first.getId());
  assertThat(index.existsById(first.getId())).isFalse();
 }
 @Test void editingWhileEmbeddingRunsCannotPublishOldVector() {
  first.edit("first revision");first=entries.save(first);worker.enqueue(first);
  when(embedding.embed(anyList())).thenAnswer(inv->{
   first.edit("second revision");first=entries.save(first);worker.enqueue(first);
   return new EmbeddingResult("text-embedding-3-small",3,List.of(List.of(1.0,0.0,0.0)));
  });
  worker.runNext();
  assertThat(index.findById(first.getId()).orElseThrow().ready()).isFalse();
  assertThat(index.findById(first.getId()).orElseThrow().getSourceHash()).isEqualTo(EmbeddingText.hash("second revision"));
 }
 @Test void providerFailureRetainsOriginalAndSchedulesRetry() {
  first.edit("retry text");first=entries.save(first);worker.enqueue(first);
  when(embedding.embed(anyList())).thenThrow(new IllegalStateException("provider down"));
  worker.runNext();
  assertThat(index.findById(first.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
  assertThat(entries.findById(first.getId()).orElseThrow().getTranscript()).isEqualTo("retry text");
 }
 @Test void backfillOnlyIndexesAddons() {
  index.deleteAll();
  var own=entries.save(MemoryEntry.confirm(photo.getRoomId(),photo.getId(),owner.getId(),EntryKind.FIRST,"TEXT",null,null,"첫 한마디"));
  worker.backfill();
  assertThat(index.count()).isEqualTo(2);
  assertThat(index.existsById(own.getId())).isFalse();
 }
 @Test void originalEditApiEnqueuesIndexInSameTransaction() throws Exception {
  var room=rooms.findById(photo.getRoomId()).orElseThrow();room.startCirculating();rooms.save(room);
  mvc.perform(patch("/api/v1/entries/"+first.getId()).session(session(guestUser)).with(csrf())
    .contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("transcript","API로 고친 기억"))))
    .andExpect(status().isOk());
  assertThat(entries.findById(first.getId()).orElseThrow().getTranscript()).isEqualTo("API로 고친 기억");
  assertThat(index.findById(first.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
 }
 @Test void failuresStopAfterThreeAttemptsAndStaleLeaseCanRecover() {
  first.edit("retry three");first=entries.save(first);worker.enqueue(first);
  when(embedding.embed(anyList())).thenThrow(new IllegalStateException("down"));
  for(int i=0;i<3;i++) {
   tx.executeWithoutResult(t->{var row=index.findById(first.getId()).orElseThrow();
    org.springframework.test.util.ReflectionTestUtils.setField(row,"nextAttemptAt",java.time.LocalDateTime.now().minusSeconds(1));});
   assertThat(worker.runNext()).isTrue();
  }
  assertThat(index.findById(first.getId()).orElseThrow().getStatus()).isEqualTo("FAILED");
  first.edit("recovery");first=entries.save(first);worker.enqueue(first);
  tx.executeWithoutResult(t->{var row=index.findById(first.getId()).orElseThrow();row.claim();
   org.springframework.test.util.ReflectionTestUtils.setField(row,"nextAttemptAt",java.time.LocalDateTime.now().minusSeconds(1));});
  doReturn(new EmbeddingResult("text-embedding-3-small",3,List.of(List.of(1.0,0.0,0.0)))).when(embedding).embed(anyList());
  assertThat(worker.runNext()).isTrue();
  assertThat(index.findById(first.getId()).orElseThrow().ready()).isTrue();
 }
 @Test void tokenizerPreservesKoreanEmojiAndSpecialTokenLiteral() {
  String text="한글👨‍👩‍👧‍👦 <|endoftext|> ".repeat(200);
  var chunks=EmbeddingText.chunks(text);
  assertThat(String.join("",chunks)).isEqualTo(text);
  var encoding=com.knuddels.jtokkit.Encodings.newDefaultEncodingRegistry().getEncoding(com.knuddels.jtokkit.api.EncodingType.CL100K_BASE);
  assertThat(chunks).allSatisfy(c->assertThat(encoding.countTokensOrdinary(c)).isLessThanOrEqualTo(1024));
 }
}
