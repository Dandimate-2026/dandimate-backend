package com.dandimate.durooduroo.ai.chat;
import java.util.List;
public record ChatResult(String intent,String outcome,List<Statement> statements,List<Support> relevantEvidence,ChatCommand.Context context,java.util.Map<String,String> interpretation,ChatCommand.Decision decision) {
 public ChatResult(String outcome,List<Statement> statements,List<Support> relevantEvidence){this("FACT",outcome,statements,relevantEvidence,ChatCommand.Context.empty());}
 public ChatResult(String intent,String outcome,List<Statement> statements,List<Support> relevantEvidence){this(intent,outcome,statements,relevantEvidence,ChatCommand.Context.empty());}
 public ChatResult(String intent,String outcome,List<Statement> statements,List<Support> relevantEvidence,ChatCommand.Context context){this(intent,outcome,statements,relevantEvidence,context,java.util.Map.of());}
 public ChatResult(String intent,String outcome,List<Statement> statements,List<Support> relevantEvidence,ChatCommand.Context context,java.util.Map<String,String> interpretation){this(intent,outcome,statements,relevantEvidence,context,interpretation,ChatCommand.Decision.empty());}
 public ChatResult {interpretation=java.util.Map.copyOf(interpretation);statements=List.copyOf(statements);relevantEvidence=List.copyOf(relevantEvidence);}
 public record Statement(String kind,String text,List<Support> supports,List<String> memoryRefs,String answersFacet) {
  public Statement(String kind,String text,List<Support> supports,List<String> memoryRefs){this(kind,text,supports,memoryRefs,"OTHER");}
  public Statement(String kind,String text,List<Support> supports){this(kind,text,supports,List.of());}
  public Statement(String text,List<Support> supports){this("FACT",text,supports);}
  public Statement {supports=List.copyOf(supports);memoryRefs=List.copyOf(memoryRefs);}
 }
 public record Support(String entryId,String quote) {}
}
