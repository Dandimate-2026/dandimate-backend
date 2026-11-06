package com.dandimate.durooduroo.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChatInputAssemblerTest {
 private ChatCommand.Evidence entry(String id,String room,String photo,String author,String nickname){
  return new ChatCommand.Evidence(id,photo,room,"방장",nickname,"내가 파란 우산을 찾았어. 춤은 안 췄어.","ADDON","2026-11-05T10:01:00+09:00",room,"2026-10-31",author,"host");
 }
 @Test void preservesEveryOriginalAndSeparatesSameNamesWhileSharingUserIdentityAcrossRooms(){
  var es=List.of(entry("e1","r1","p1","alice","민주"),entry("e2","r1","p1","bob","민주"),entry("e3","r2","p2","alice","다른별명"));
  var payload=new ObjectMapper().valueToTree(ChatInputAssembler.assemble(new ChatCommand("누가?",es,"alice"),Map.of("e1","record_1","e2","record_2","e3","record_3")));
  var people=payload.path("people");var r1=payload.path("rooms").get(0).path("photos").get(0);var r2=payload.path("rooms").get(1).path("photos").get(0);
  var first=people.path(r1.path("entries").get(0).path("author").asText());
  var second=people.path(r1.path("entries").get(1).path("author").asText());
  var third=people.path(r2.path("entries").get(0).path("author").asText());
  assertThat(first.path("userId")).isEqualTo(payload.path("viewerUserId")).isEqualTo(third.path("userId")).isNotEqualTo(second.path("userId"));
  assertThat(first.path("nickname").asText()).isEqualTo("민주");assertThat(third.path("nickname").asText()).isEqualTo("다른별명");
  assertThat(people.path(r1.path("uploader").asText()).path("userId")).isNotEqualTo(first.path("userId"));
  int i=0;for(var room:payload.path("rooms"))for(var photo:room.path("photos"))for(var e:photo.path("entries")){
   var original=es.get(i++);assertThat(e.path("text").asText()).isEqualTo(original.text());
   assertThat(e.path("createdAt").asText()).isEqualTo(original.createdAt());
   assertThat(e.path("entryId").asText()).isEqualTo("record_"+i);
  }
  assertThat(i).isEqualTo(es.size());
 }
 @Test void retainsJudgmentsAndOrdinalDirectionWithoutMergingUnknownPeople(){
  var decision=new ChatCommand.Decision("활동성","전체",List.of("A","B","C"),"C","LOW","C를 선택했어요.","",1);
  var context=new ChatCommand.Context("여행","친구","활동성","LOW",2);
  var turn=new ChatCommand.Turn("judgment_1",context,List.of("e1"),decision);
  var payload=new ObjectMapper().valueToTree(ChatInputAssembler.assemble(new ChatCommand("그 다음은?",List.of(entry("e1","r1","p1",null,"동명"),entry("e2","r1","p1",null,"동명")),null,List.of(turn)),Map.of("e1","record_1","e2","record_2")));
  var entries=payload.path("rooms").get(0).path("photos").get(0).path("entries");
  assertThat(entries.get(0).path("author")).isNotEqualTo(entries.get(1).path("author"));
  assertThat(payload.path("contextHint").path("direction").asText()).isEqualTo("LOW");
  assertThat(payload.path("contextHint").path("rankPosition").asInt()).isEqualTo(2);
  var judgment=payload.path("assistantJudgmentsNotFacts").get(0);
  assertThat(judgment.path("decision").path("summary").asText()).isEqualTo(decision.summary());
  assertThat(judgment.path("ordinalLookup").get(2).path("LOW").asInt()).isEqualTo(1);
  assertThat(judgment.path("availableRecordRefs").get(0).asText()).isEqualTo("record_1");
 }
}
