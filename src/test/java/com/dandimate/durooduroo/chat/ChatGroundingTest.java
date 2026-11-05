package com.dandimate.durooduroo.chat;
import com.dandimate.durooduroo.chat.service.*;
import com.dandimate.durooduroo.chat.dto.*;
import com.dandimate.durooduroo.ai.chat.*;
import java.util.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
class ChatGroundingTest {
 static final ChatAuthorView AUTHOR=new ChatAuthorView("u","m","A","MEMBER","CANOLA");
 static final ChatSourceView.EntryInfo ENTRY=new ChatSourceView.EntryInfo("e","내가 춘거 맞음",LocalDateTime.now(),AUTHOR);
 static final ChatSourceView SOURCE=new ChatSourceView(new ChatSourceView.RoomInfo("r","경주"),new ChatSourceView.PhotoInfo("p","url",LocalDateTime.now(),AUTHOR),List.of(ENTRY));
 static Map<String,ChatRecordAccess.Record> records(){return Map.of("e",new ChatRecordAccess.Record("e","hash","v1","ADDON",SOURCE,ENTRY),"e2",new ChatRecordAccess.Record("e2","hash2","v1","ADDON",SOURCE,new ChatSourceView.EntryInfo("e2","나는 사진을 찍었어",LocalDateTime.now(),AUTHOR)));}
 static ChatResult result(String id,String quote){
  var support=new ChatResult.Support(id,quote);
  return new ChatResult("ANSWER",List.of(new ChatResult.Statement("A가 춤췄네요.",List.of(support))),List.of(support));
 }
 static ChatMessageResponse run(ChatResult output,boolean revoke,AtomicInteger calls){
  return run(output,revoke,calls,"누가 춤췄지");
 }
 static ChatMessageResponse run(ChatResult output,boolean revoke,AtomicInteger calls,String question){
  return run(output,revoke,calls,question,List.of());
 }
 static ChatMessageResponse run(ChatResult output,boolean revoke,AtomicInteger calls,String question,List<ChatCommand.Turn> history){
  var loads=new AtomicInteger();
  var access=new ChatRecordAccess(null,null,null,null,null,null){
   public Map<String,ChatRecordAccess.Record> load(String user,boolean urls){return revoke&&loads.incrementAndGet()==3?Map.of():records();}
  };
  var retrieval=new ChatRetrievalService(null,null){public List<String> retrieve(String q,Map<String,ChatRecordAccess.Record> r){return List.of("p");}};
  return new ChatService(access,retrieval,c->{calls.incrementAndGet();return output;}).answer("u",question,history);
 }
 @org.junit.jupiter.api.Test void verifiesGroundingAndOrdinalContracts(){
  var calls=new AtomicInteger();var valid=run(result("e","내가 춘거 맞음"),false,calls);
  check(valid.sources().size()==1&&valid.citations().size()==1,"valid citations");check(calls.get()==1,"one LLM call");
  check(run(result("e","B가 춤췄음"),false,new AtomicInteger()).sources().isEmpty(),"fabricated quote blocked");
  check(run(result("missing","내가 춘거 맞음"),false,new AtomicInteger()).sources().isEmpty(),"unknown source blocked");
  var noSupport=new ChatResult("ANSWER",List.of(new ChatResult.Statement("A가 춤췄네요.",List.of())),List.of());
  check(run(noSupport,false,new AtomicInteger()).sources().isEmpty(),"unsupported statement blocked");
  var uncertain=new ChatResult("INSUFFICIENT",List.of(),List.of());
  check(run(uncertain,false,new AtomicInteger()).sources().isEmpty(),"insufficient evidence refused");
  boolean rejected=false;try{run(result("e","내가 춘거 맞음"),true,new AtomicInteger());}catch(com.dandimate.durooduroo.common.exception.BusinessException expected){rejected=true;}
  check(rejected,"revoked access blocked");
  var cited=new ChatResult.Support("e","내가 춘거 맞음");
  var reviewed=new ChatResult.Support("e2","나는 사진을 찍었어");
  var opinion=new ChatResult.Statement("OPINION","춤을 춘 순간이 즐거웠을 것 같아요.",List.of(cited));
  var factual=new ChatResult.Statement("FACT","A가 춤췄다는 기록이 있어요.",List.of(cited));
  check(!run(new ChatResult("OPINION","ANSWER",List.of(opinion),List.of(cited,reviewed)),false,new AtomicInteger()).sources().isEmpty(),"reviewed but uncited record allowed");
  check(!run(new ChatResult("MIXED","ANSWER",List.of(factual,opinion),List.of(cited)),false,new AtomicInteger()).sources().isEmpty(),"mixed answer allowed");
  check(run(new ChatResult("FACT","ANSWER",List.of(opinion),List.of(cited)),false,new AtomicInteger()).sources().isEmpty(),"opinion cannot masquerade as fact intent");
  check(run(new ChatResult("OPINION","ANSWER",List.of(new ChatResult.Statement("OPINION","재미있었겠어요",List.of(new ChatResult.Support("e","함께 웃었어")))),List.of(cited)),false,new AtomicInteger()).sources().isEmpty(),"opinion invented quote blocked");
  check(run(new ChatResult("OPINION","ANSWER",List.of(opinion),List.of(new ChatResult.Support("unknown","없는 기록"))),false,new AtomicInteger()).sources().isEmpty(),"unknown reviewed reference blocked");
  var prior=new ChatCommand.Decision("활동성","참가자",List.of("A","B"),"A","HIGH","A가 가장 활동적으로 보인다고 답했음","");
  var memory=new ChatCommand.Turn("judgment_test",ChatCommand.Context.empty(),List.of("e"),prior);
  var self=new ChatResult.Statement("CONVERSATION","앞서 A를 가장 활동적으로 보인다고 답했어요.",List.of(),List.of("judgment_test"));
  var explanation=new ChatResult("FACT","ANSWER",List.of(self),List.of());
  check(run(explanation,false,new AtomicInteger(),"전에 뭐라고 했어?",List.of(memory)).answer().contains("앞서 A"),"verified self-reference allowed without photo quote");
  check(run(explanation,false,new AtomicInteger(),"전에 뭐라고 했어?",List.of()).answer().contains("근거 연결"),"unknown self-reference blocked");
  var mismatch=new ChatCommand.Decision("활동성","참가자",List.of("A","B"),"A","LOW","A를 최저로 선택","");
  check(run(new ChatResult("OPINION","ANSWER",List.of(opinion),List.of(cited),ChatCommand.Context.empty(),Map.of(),mismatch),false,new AtomicInteger()).sources().isEmpty(),"ranking direction mismatch blocked");
  var unrelated=new ChatResult.Statement("FACT","금액도 알려드릴게요.",List.of(cited),List.of(),"AMOUNT");
  check(run(new ChatResult("FACT","ANSWER",List.of(unrelated),List.of(),ChatCommand.Context.empty(),Map.of("requestedFacets","LOCATION")),false,new AtomicInteger()).sources().isEmpty(),"unrequested facet blocked");
  var matched=new ChatResult.Statement("FACT","기록이 있어요.",List.of(cited),List.of(),"ACTIVITY");
  check(!run(new ChatResult("FACT","ANSWER",List.of(matched),List.of(),ChatCommand.Context.empty(),Map.of("requestedFacets","ACTIVITY")),false,new AtomicInteger()).sources().isEmpty(),"requested facet allowed");
  var linked=new ChatResult("OPINION","ANSWER",List.of(self,opinion),List.of(cited),ChatCommand.Context.empty(),Map.of("requestedFacets","OTHER"));
  check(run(linked,false,new AtomicInteger(),"반대쪽은?",List.of(memory)).answer().contains("앞서 A"),"conversation explanation does not reject valid opinion");
  var failed=new ChatResult("OPINION","ANSWER",List.of(new ChatResult.Statement("OPINION","잘못된 인용",List.of(new ChatResult.Support("missing","원문 없음")))),List.of(),new ChatCommand.Context("평가","친구","재미"),Map.of("subject","친구","criterion","재미","direction","LOW"));
  var preserved=run(failed,false,new AtomicInteger());
  check(preserved.context().direction().equals("LOW")&&preserved.context().target().equals("친구"),"failed answer preserves latest request direction");
  check(preserved.decision().summary().isEmpty(),"rejected answer never becomes a remembered judgment");
  for(String dir:List.of("HIGH","LOW"))for(int rank=1;rank<=3;rank++){
   var order=List.of("A","B","C");String pick=order.get(dir.equals("HIGH")?rank-1:order.size()-rank);
   var nth=new ChatCommand.Decision("활동성","참가자",order,pick,dir,"순위 의견", "",rank);
   check(!run(new ChatResult("OPINION","ANSWER",List.of(opinion),List.of(cited),ChatCommand.Context.empty(),Map.of(),nth),false,new AtomicInteger()).sources().isEmpty(),"Nth position allowed "+dir+rank);
  }
  var outside=new ChatCommand.Decision("활동성","참가자",List.of("A","B"),"A","HIGH","범위 초과","",3);
  check(run(new ChatResult("OPINION","ANSWER",List.of(opinion),List.of(cited),ChatCommand.Context.empty(),Map.of(),outside),false,new AtomicInteger()).sources().isEmpty(),"out-of-range ranking blocked");
 }
 static void check(boolean pass,String name){if(!pass)throw new AssertionError(name);}
}
