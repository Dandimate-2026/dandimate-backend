package com.dandimate.durooduroo.ai.chat;
import java.util.List;
public record ChatCommand(String question,List<Evidence> evidence,String viewerUserId,List<Turn> conversationState) {
 public ChatCommand(String question,List<Evidence> evidence){this(question,evidence,null,List.of());}
 public ChatCommand(String question,List<Evidence> evidence,String viewerUserId){this(question,evidence,viewerUserId,List.of());}
 public record Context(String topic,String target,String criterion,String direction,int rankPosition) {
  public Context(String topic,String target,String criterion,String direction){this(topic,target,criterion,direction,1);}
  public Context(String topic,String target,String criterion){this(topic,target,criterion,"NONE");}public static Context empty(){return new Context("","","");}}
 public record Decision(String criterion,String scope,List<String> rankedTargets,String selectedTarget,String direction,String summary,String revisesId,int rankPosition) {
  public Decision(String criterion,String scope,List<String> rankedTargets,String selectedTarget,String direction,String summary,String revisesId){this(criterion,scope,rankedTargets,selectedTarget,direction,summary,revisesId,1);}
  public Decision {rankedTargets=List.copyOf(rankedTargets);}
  public static Decision empty(){return new Decision("","",List.of(),"","NONE","","");}
 }
 public record Turn(String id,Context context,List<String> citedEntryIds,Decision decision) {
  public Turn(Context context,List<String> citedEntryIds){this("",context,citedEntryIds,Decision.empty());}
  public Turn {citedEntryIds=List.copyOf(citedEntryIds);}
 }
 public ChatCommand {conversationState=List.copyOf(conversationState);evidence=List.copyOf(evidence);}
 public record Evidence(String entryId,String photoId,String roomName,String uploaderNickname,String authorNickname,String text,
                        String kind,String createdAt,String roomId,String meetingDate,String authorUserId,String uploaderUserId,
                        String placeName,String address,String city) {
  public Evidence(String entryId,String photoId,String roomName,String uploaderNickname,String authorNickname,String text){
   this(entryId,photoId,roomName,uploaderNickname,authorNickname,text,"ADDON",null,null,null,null,null,null,null,null);
  }
  public Evidence(String entryId,String photoId,String roomName,String uploaderNickname,String authorNickname,String text,
                  String kind,String createdAt,String roomId,String meetingDate,String authorUserId,String uploaderUserId){
   this(entryId,photoId,roomName,uploaderNickname,authorNickname,text,kind,createdAt,roomId,meetingDate,authorUserId,uploaderUserId,null,null,null);
  }
 }
}
