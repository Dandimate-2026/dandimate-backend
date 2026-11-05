package com.dandimate.durooduroo.ai.chat;
import java.util.*;
/** Request-local, authorized evidence graph. No prior messages or generated answers. */
final class ChatInputAssembler {
 static Map<String,Object> assemble(ChatCommand command,Map<String,String> aliases){
  Map<String,Object> payload=new LinkedHashMap<>();
  payload.put("contextHint",command.conversationState().isEmpty()?Map.of():command.conversationState().get(command.conversationState().size()-1).context());
  payload.put("lastRequestAnswered",command.conversationState().isEmpty()||!command.conversationState().get(command.conversationState().size()-1).decision().summary().isBlank());
  payload.put("assistantJudgmentsNotFacts",command.conversationState().stream().filter(t->!t.decision().summary().isBlank())
    .map(t->Map.of("id",t.id(),"decision",t.decision(),"ordinalLookup",ordinalLookup(t.decision()),"availableRecordRefs",t.citedEntryIds().stream().filter(aliases::containsKey).map(aliases::get).toList())).toList());
  payload.put("viewerUserId",command.viewerUserId());
  Map<String,Map<String,Object>> rooms=new LinkedHashMap<>();
  Map<String,Map<String,Object>> photos=new LinkedHashMap<>();
  for(var e:command.evidence()){
   String roomKey=Objects.toString(e.roomId(),e.roomName());
   var room=rooms.computeIfAbsent(roomKey,k->{Map<String,Object> x=new LinkedHashMap<>();x.put("roomId",k);x.put("name",e.roomName());x.put("meetingDate",e.meetingDate());x.put("photos",new ArrayList<Map<String,Object>>());return x;});
   var photo=photos.get(e.photoId());
   if(photo==null){photo=new LinkedHashMap<>();photo.put("photoId",e.photoId());photo.put("uploader",person(e.uploaderUserId(),e.uploaderNickname()));photo.put("entries",new ArrayList<Map<String,Object>>());photos.put(e.photoId(),photo);((List<Map<String,Object>>)room.get("photos")).add(photo);}
   Map<String,Object> entry=new LinkedHashMap<>();entry.put("entryId",aliases.get(e.entryId()));entry.put("kind",e.kind());entry.put("author",person(e.authorUserId(),e.authorNickname()));entry.put("createdAt",e.createdAt());entry.put("text",e.text());
   ((List<Map<String,Object>>)photo.get("entries")).add(entry);
  }
  payload.put("rooms",new ArrayList<>(rooms.values()));
  payload.put("question",command.question());
  return payload;
 }
 static List<Map<String,Object>> ordinalLookup(ChatCommand.Decision decision){
  List<Map<String,Object>> rows=new ArrayList<>();var ranking=decision.rankedTargets();
  for(int i=0;i<ranking.size();i++)rows.add(Map.of("target",ranking.get(i),"HIGH",i+1,"LOW",ranking.size()-i));
  return rows;
 }
 private static Map<String,Object> person(String id,String nickname){Map<String,Object> p=new LinkedHashMap<>();p.put("userId",id);p.put("nickname",nickname);return p;}
}
