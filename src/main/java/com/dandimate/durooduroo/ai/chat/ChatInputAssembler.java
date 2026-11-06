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
  // Aliases are request-local. Database identifiers and API responses remain unchanged.
  Map<String,String> userIds=new LinkedHashMap<>();
  Map<List<String>,String> personRefs=new LinkedHashMap<>();
  Map<String,Map<String,Object>> people=new LinkedHashMap<>();
  payload.put("viewerUserId",userAlias(command.viewerUserId(),userIds));
  payload.put("people",people);
  Map<String,Map<String,Object>> rooms=new LinkedHashMap<>();
  Map<String,Map<String,Object>> photos=new LinkedHashMap<>();
  for(var e:command.evidence()){
   String roomKey=Objects.toString(e.roomId(),e.roomName());
   var room=rooms.computeIfAbsent(roomKey,k->{Map<String,Object> x=new LinkedHashMap<>();x.put("roomId","room_"+(rooms.size()+1));x.put("name",e.roomName());x.put("meetingDate",e.meetingDate());x.put("photos",new ArrayList<Map<String,Object>>());return x;});
   var photo=photos.get(e.photoId());
   if(photo==null){photo=new LinkedHashMap<>();photo.put("photoId","photo_"+(photos.size()+1));photo.put("uploader",personRef(roomKey,e.uploaderUserId(),e.uploaderNickname(),userIds,personRefs,people));photo.put("entries",new ArrayList<Map<String,Object>>());photos.put(e.photoId(),photo);((List<Map<String,Object>>)room.get("photos")).add(photo);}
   Map<String,Object> entry=new LinkedHashMap<>();entry.put("entryId",aliases.get(e.entryId()));entry.put("kind",e.kind());entry.put("author",personRef(roomKey,e.authorUserId(),e.authorNickname(),userIds,personRefs,people));entry.put("createdAt",e.createdAt());entry.put("text",e.text());
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
 private static String userAlias(String id,Map<String,String> users){
  return id==null||id.isBlank()?null:users.computeIfAbsent(id,k->"user_"+(users.size()+1));
 }
 private static String personRef(String room,String id,String nickname,Map<String,String> users,
                               Map<List<String>,String> refs,Map<String,Map<String,Object>> people){
  String user=userAlias(id,users);
  // Do not merge unknown identities by nickname. A user can have different room nicknames.
  List<String> key=List.of(room,user==null?"unknown_"+people.size():user,Objects.toString(nickname,""));
  return refs.computeIfAbsent(key,k->{
   String ref="person_"+(people.size()+1);
   Map<String,Object> person=new LinkedHashMap<>();person.put("userId",user);person.put("nickname",nickname);
   people.put(ref,person);return ref;
  });
 }
}
