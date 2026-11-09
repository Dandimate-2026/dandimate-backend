package com.dandimate.durooduroo.chat.service;
import com.dandimate.durooduroo.ai.chat.*;
import com.dandimate.durooduroo.ai.common.AiFailure;
import com.dandimate.durooduroo.chat.dto.*;
import com.dandimate.durooduroo.common.exception.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
/** Retrieves all authorized room records and returns the model's structured answer. */
@Service
public class ChatService {
 private final ChatRecordAccess access;private final ChatRetrievalService retrieval;private final ChatAiService ai;
 private ChatIntentRouter router;
 @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.chat.quota.ChatQuotaService quota;
 private final Semaphore capacity=new Semaphore(4);
 public ChatService(ChatRecordAccess access,ChatRetrievalService retrieval,ChatAiService ai){this.access=access;this.retrieval=retrieval;this.ai=ai;}
 @org.springframework.beans.factory.annotation.Autowired
 public ChatService(ChatRecordAccess access,ChatRetrievalService retrieval,ChatAiService ai,ChatIntentRouter router){
  this(access,retrieval,ai);this.router=router;
 }
 public ChatMessageResponse answer(String userId,String question){
  return answer(userId,question,List.of());
 }
 public ChatMessageResponse answer(String userId,String question,List<ChatCommand.Turn> history){
  if(!capacity.tryAcquire())throw new BusinessException(ErrorCode.AI_TEMPORARILY_UNAVAILABLE,"잠시 후 다시 질문해 주세요.");
  try{return respond(userId,question.strip(),history);}
  catch(AiFailure failure){throw new BusinessException(ErrorCode.AI_TEMPORARILY_UNAVAILABLE,"AI 답변을 준비하지 못했어요. 잠시 후 다시 시도해 주세요.");}
  finally{capacity.release();}
 }
 private ChatMessageResponse respond(String userId,String question,List<ChatCommand.Turn> history){
  if(router!=null){
   var route=router.route(question,!history.isEmpty());
   if(!route.kind().equals("RECORD"))return new ChatMessageResponse(route.reply(),List.of()).withOutcome(route.kind());
  }
  if(quota!=null) {
   var attrs=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
   var request=attrs instanceof org.springframework.web.context.request.ServletRequestAttributes a?a.getRequest():null;
   String key=request==null?null:request.getHeader("Idempotency-Key");
   return quota.execute(userId,key,question,()->respondRecords(userId,question,history));
  }
  return respondRecords(userId,question,history);
 }
 private ChatMessageResponse respondRecords(String userId,String question,List<ChatCommand.Turn> history) {
  var allowed=access.load(userId,false);if(allowed.isEmpty())return insufficient();
  // Conversation is a reference, never an authorization source. Recheck all old IDs.
  var safeHistory=history.stream().skip(Math.max(0,history.size()-8))
    .filter(t->t.citedEntryIds().stream().allMatch(allowed::containsKey))
    .map(t->new ChatCommand.Turn(t.id(),t.context(),t.citedEntryIds(),t.decision())).toList();
  var rankedPhotos=retrieval.retrieve(question,allowed); // Current question only; no previous question concatenation.
  // Every authorized photo remains eligible: broad questions must not be rejected or cut to top-N photos.
  // The retrieval index ranks the complete corpus; grouped input avoids repeating room/photo metadata per entry.
  Map<String,Integer> rank=new HashMap<>();for(int i=0;i<rankedPhotos.size();i++)rank.put(rankedPhotos.get(i),i);
  List<ChatRecordAccess.Record> selected=new ArrayList<>(allowed.values());
  selected.sort(Comparator.comparingInt(r->rank.getOrDefault(r.source().photo().photoId(),Integer.MAX_VALUE)));
  var before=access.load(userId,false);unchanged(selected,before);
  var evidence=selected.stream().map(r->{var fresh=before.get(r.entryId());var source=fresh.source();
   return new ChatCommand.Evidence(r.entryId(),source.photo().photoId(),source.room().name(),source.photo().uploader().nickname(),
    fresh.entry().author().nickname(),fresh.entry().text(),fresh.kind(),fresh.entry().createdAt().toString(),source.room().roomId(),
    source.room().meetingDate()==null?null:source.room().meetingDate().toString(),fresh.entry().author().userId(),source.photo().uploader().userId(),
    source.photo().placeName(),source.photo().address(),source.photo().city());
  }).toList();
  var result=ai.answer(new ChatCommand(question,evidence,userId,safeHistory)); // Exactly one LLM call, including refusal/invalid output.
  var after=access.load(userId,true);unchanged(selected,after);
  if(!Set.of("FACT","OPINION","MIXED").contains(result.intent()))return invalidEvidence(result);
  if(!Set.of("ANSWER","CONFLICT","INSUFFICIENT").contains(result.outcome()))return invalidEvidence(result);
  if(result.outcome().equals("INSUFFICIENT"))return insufficient(result);
  if(result.statements().isEmpty()||result.statements().size()>12)return invalidEvidence(result);
  Set<String> memoryIds=new HashSet<>();safeHistory.forEach(t->memoryIds.add(t.id()));
  var decision=result.decision();
  List<ChatMessageResponse.Citation> citations=new ArrayList<>();List<String> sentences=new ArrayList<>();
  Map<String,ChatSourceView> sources=new LinkedHashMap<>();
  for(int i=0;i<result.statements().size();i++){
   var statement=result.statements().get(i);String text=statement.text();
   if(!Set.of("FACT","OPINION","CONVERSATION").contains(statement.kind()))return invalidEvidence(result);
   if(text==null||text.isBlank()||text.length()>1800)return invalidEvidence(result);
   if(statement.kind().equals("CONVERSATION")&&!memoryIds.containsAll(statement.memoryRefs()))return invalidEvidence(result);
   if(text.matches("(?s).*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*"))return invalidEvidence(result);
   for(var support:statement.supports()){
    var record=after.get(support.entryId());
    if(record==null||support.quote()==null||support.quote().isBlank())continue;
    citations.add(new ChatMessageResponse.Citation(i,record.entryId(),record.source().photo().photoId(),record.kind(),record.entry().text(),record.entry().author()));
    sources.putIfAbsent(record.source().photo().photoId(),record.source());
   }
   sentences.add(text);
  }
  // Citations are optional UI hints; exact source-text matching does not gate natural answers.
  String answer=String.join("\n\n",sentences);if(answer.length()>6000)return invalidEvidence(result);
  return new ChatMessageResponse(answer,List.copyOf(sources.values()),citations,stateOf(result),decision).withOutcome(result.outcome());
 }
 private static void unchanged(List<ChatRecordAccess.Record> original,Map<String,ChatRecordAccess.Record> current){
  for(var r:original){var now=current.get(r.entryId());if(now==null||!now.photoVersion().equals(r.photoVersion()))
   throw new BusinessException(ErrorCode.VERSION_CONFLICT,"기록이나 열람 권한이 바뀌었어요. 다시 질문해 주세요.");}
 }
 private static ChatCommand.Context stateOf(ChatResult result){
  // Store only the interpreted request, including its direction, even if answer validation fails.
  var meaning=result.interpretation();
  return new ChatCommand.Context(result.context().topic(),meaning.getOrDefault("subject",result.context().target()),
    meaning.getOrDefault("criterion",result.context().criterion()),meaning.getOrDefault("direction","NONE"),result.decision().rankPosition());
 }
 private ChatMessageResponse insufficient(ChatResult result){
  return new ChatMessageResponse(insufficient().answer(),List.of(),List.of(),stateOf(result),ChatCommand.Decision.empty()).withOutcome("INSUFFICIENT");
 }
 private ChatMessageResponse invalidEvidence(ChatResult result){
  return new ChatMessageResponse("답변을 정리하는 과정에서 근거 연결에 문제가 생겼어요. 방금 질문의 맥락은 유지하고 있어요. 다시 말씀해 주시면 그 질문을 기준으로 답할게요.",List.of(),List.of(),stateOf(result),ChatCommand.Decision.empty()).withOutcome("INVALID");
 }
 private ChatMessageResponse insufficient(){return new ChatMessageResponse("지금 확인할 수 있는 기록만으로는 답하기 어려워요. 어느 때나 어떤 사진의 이야기인지 조금 더 알려주시겠어요?",List.of()).withOutcome("INSUFFICIENT");}
 private ChatMessageResponse invalidEvidence(){return new ChatMessageResponse("답변을 뒷받침하는 원문을 정확히 확인하지 못했어요. 추측해서 말씀드리기보다는, 질문을 조금 더 구체적으로 받아볼게요.",List.of()).withOutcome("INVALID");}
}
