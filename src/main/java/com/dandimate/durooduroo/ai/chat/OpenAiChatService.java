package com.dandimate.durooduroo.ai.chat;
import com.dandimate.durooduroo.ai.common.AiFailure;
import com.dandimate.durooduroo.ai.openai.OpenAiResponsesClient;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
@Service
public class OpenAiChatService implements ChatAiService {
 private final ObjectProvider<OpenAiResponsesClient> clients;
 public OpenAiChatService(ObjectProvider<OpenAiResponsesClient> clients){this.clients=clients;}
 private static Map<String,Object> object(Map<String,Object> properties){
  return Map.of("type","object","additionalProperties",false,"required",new ArrayList<>(properties.keySet()),"properties",properties);
 }
 public ChatResult answer(ChatCommand command){
  var client=clients.getIfAvailable();
  if(client==null)throw new AiFailure(AiFailure.Code.NOT_CONFIGURED,false,"Chat is not configured");
  Map<String,String> aliases=new LinkedHashMap<>(),originals=new HashMap<>();
  for(var e:command.evidence()){String alias="record_"+(aliases.size()+1);aliases.put(e.entryId(),alias);originals.put(alias,e.entryId());}
  var support=object(Map.of("entryId",Map.of("type","string","enum",new ArrayList<>(originals.keySet())),"quote",Map.of("type","string")));
  var memoryIds=command.conversationState().stream().map(ChatCommand.Turn::id).filter(x->!x.isBlank()).toList();
  Map<String,Object> memoryItem=memoryIds.isEmpty()?Map.of("type","string"):Map.of("type","string","enum",memoryIds);
  var facet=Map.of("type","string","enum",List.of("LOCATION","PERSON","TIME","AMOUNT","ACTIVITY","REASON","EVALUATION","CONVERSATION","OTHER"));
  List<Map<String,Object>> variants=new ArrayList<>();
  for(String kind:List.of("FACT","OPINION"))variants.add(object(Map.of("answersFacet",facet,"kind",Map.of("type","string","enum",List.of(kind)),"text",Map.of("type","string"),"supports",Map.of("type","array","items",support),"memoryRefs",Map.of("type","array","maxItems",0,"items",Map.of("type","string")))));
  if(!memoryIds.isEmpty())variants.add(object(Map.of("answersFacet",facet,"kind",Map.of("type","string","enum",List.of("CONVERSATION")),"text",Map.of("type","string"),"supports",Map.of("type","array","maxItems",0,"items",support),"memoryRefs",Map.of("type","array","minItems",1,"items",memoryItem))));
  var statement=Map.of("anyOf",variants);
  Map<String,Object> fields=new LinkedHashMap<>();
  fields.put("interpretation",object(new LinkedHashMap<>(Map.of(
    "requestedFacets",Map.of("type","array","minItems",1,"items",facet),"subject",Map.of("type","string"),"criterion",Map.of("type","string"),
    "direction",Map.of("type","string","enum",List.of("HIGH","LOW","NONE"))))));
  fields.put("intent",Map.of("type","string","enum",List.of("FACT","OPINION","MIXED")));
  fields.put("outcome",Map.of("type","string","enum",List.of("ANSWER","CONFLICT","INSUFFICIENT")));
  Map<String,Object> decisionFields=new LinkedHashMap<>();
  decisionFields.put("criterion",Map.of("type","string","maxLength",160));
  decisionFields.put("scope",Map.of("type","string","maxLength",160));
  decisionFields.put("direction",Map.of("type","string","enum",List.of("HIGH","LOW","NONE")));
  decisionFields.put("rankPosition",Map.of("type","integer","minimum",1));
  decisionFields.put("rankedTargets",Map.of("type","array","items",Map.of("type","string")));
  decisionFields.put("selectedTarget",Map.of("type","string"));
  decisionFields.put("revisesId",Map.of("type","string"));
  decisionFields.put("summary",Map.of("type","string","maxLength",360));
  fields.put("decision",object(decisionFields));
  fields.put("statements",Map.of("type","array","items",statement));
  fields.put("context",object(Map.of("topic",Map.of("type","string","maxLength",120))));
  var schema=object(fields);
  // Short, request-local references avoid UUID transcription mistakes by the model.
  var input=ChatInputAssembler.assemble(command,aliases);
  var output=client.call("chat-v3","memory_chat_grounded",schema,input).output();
  List<ChatResult.Statement> statements=new ArrayList<>();
  if(!output.path("outcome").isTextual()||!output.path("statements").isArray())invalid();
  for(var item:output.path("statements")){
   if(!item.path("text").isTextual()||!item.path("supports").isArray())invalid();
   List<ChatResult.Support> supports=new ArrayList<>();
   for(var s:item.path("supports")){
    if(!s.path("entryId").isTextual()||!s.path("quote").isTextual())invalid();
    supports.add(new ChatResult.Support(originals.getOrDefault(s.path("entryId").asText(),"INVALID_REFERENCE"),s.path("quote").asText()));
   }
   List<String> memories=new ArrayList<>();item.path("memoryRefs").forEach(m->memories.add(m.asText()));
   statements.add(new ChatResult.Statement(item.path("kind").asText(),item.path("text").asText(),supports,memories,item.path("answersFacet").asText()));
  }
  List<ChatResult.Support> relevant=new ArrayList<>();
  for(var s:output.path("relevantEvidence")){
   if(!s.path("entryId").isTextual()||!s.path("quote").isTextual())invalid();
   relevant.add(new ChatResult.Support(originals.getOrDefault(s.path("entryId").asText(),"INVALID_REFERENCE"),s.path("quote").asText()));
  }
  var decisionNode=output.path("decision");List<String> ranking=new ArrayList<>();decisionNode.path("rankedTargets").forEach(x->ranking.add(x.asText()));
  var decision=new ChatCommand.Decision(decisionNode.path("criterion").asText(),decisionNode.path("scope").asText(),ranking,decisionNode.path("selectedTarget").asText(),decisionNode.path("direction").asText(),decisionNode.path("summary").asText(),decisionNode.path("revisesId").asText(),decisionNode.path("rankPosition").asInt(1));
  List<String> requestedFacets=new ArrayList<>();output.path("interpretation").path("requestedFacets").forEach(x->requestedFacets.add(x.asText()));
  return new ChatResult(output.path("intent").asText(),output.path("outcome").asText(),statements,relevant,new ChatCommand.Context(output.path("context").path("topic").asText(),output.path("interpretation").path("subject").asText(),output.path("interpretation").path("criterion").asText()),Map.of("requestedFacets",String.join(",",requestedFacets),"subject",output.path("interpretation").path("subject").asText(),"criterion",output.path("interpretation").path("criterion").asText(),"direction",output.path("interpretation").path("direction").asText()),decision);
 }
 private static void invalid(){throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,true,"Invalid grounded response");}
}
