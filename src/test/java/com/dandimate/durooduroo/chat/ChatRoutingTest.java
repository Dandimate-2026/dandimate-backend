package com.dandimate.durooduroo.chat;
import com.dandimate.durooduroo.chat.service.*;
import com.dandimate.durooduroo.ai.chat.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ChatRoutingTest {
 @Test void casualAndUnsupportedRepliesNeverReadRecordsOrEmbeddings() {
  var access=mock(ChatRecordAccess.class);var retrieval=mock(ChatRetrievalService.class);
  var ai=mock(ChatAiService.class);var router=mock(ChatIntentRouter.class);
  var chat=new ChatService(access,retrieval,ai,router);
  for(String kind:List.of("SMALL_TALK","OUT_OF_SCOPE")){
   when(router.route(anyString(),anyBoolean())).thenReturn(new ChatIntentRouter.Route(kind,"반가워요."));
   var answer=chat.answer("user","질문");
   assertThat(answer.outcome()).isEqualTo(kind);
   assertThat(answer.sources()).isEmpty();assertThat(answer.citations()).isEmpty();
  }
  verifyNoInteractions(access,retrieval,ai);
 }
 @Test void privateOrMixedRequestsStayBehindAccessChecksEvenWithNoRooms() {
  var access=mock(ChatRecordAccess.class);var retrieval=mock(ChatRetrievalService.class);
  var ai=mock(ChatAiService.class);var router=mock(ChatIntentRouter.class);
  when(router.route(anyString(),anyBoolean())).thenReturn(new ChatIntentRouter.Route("RECORD",""));
  when(access.load("user",false)).thenReturn(Map.of());
  var answer=new ChatService(access,retrieval,ai,router).answer("user","인사하고 남의 방 이야기도 알려줘");
  assertThat(answer.outcome()).isEqualTo("INSUFFICIENT");
  verify(access).load("user",false);verifyNoInteractions(retrieval,ai);
 }
}
