package com.dandimate.durooduroo.chat.dto;
import java.util.List;
public record ChatMessageResponse(String answer,List<ChatSourceView> sources,List<Citation> citations,com.dandimate.durooduroo.ai.chat.ChatCommand.Context context,com.dandimate.durooduroo.ai.chat.ChatCommand.Decision decision, String outcome, String conversationId) {
 public ChatMessageResponse(String answer,List<ChatSourceView> sources,List<Citation> citations,com.dandimate.durooduroo.ai.chat.ChatCommand.Context context,com.dandimate.durooduroo.ai.chat.ChatCommand.Decision decision){this(answer,sources,citations,context,decision,"ANSWER",null);}
 public ChatMessageResponse withOutcome(String value){return new ChatMessageResponse(answer,sources,citations,context,decision,value,conversationId);}
 public ChatMessageResponse withConversationId(String value){return new ChatMessageResponse(answer,sources,citations,context,decision,outcome,value);}
 public ChatMessageResponse(String answer,List<ChatSourceView> sources,List<Citation> citations){this(answer,sources,citations,com.dandimate.durooduroo.ai.chat.ChatCommand.Context.empty());}
 public ChatMessageResponse(String answer,List<ChatSourceView> sources,List<Citation> citations,com.dandimate.durooduroo.ai.chat.ChatCommand.Context context){this(answer,sources,citations,context,com.dandimate.durooduroo.ai.chat.ChatCommand.Decision.empty());}
 public ChatMessageResponse {sources=List.copyOf(sources);citations=List.copyOf(citations);}
 public ChatMessageResponse(String answer,List<ChatSourceView> sources){this(answer,sources,List.of());}
 public record Citation(int statementIndex,String entryId,String photoId,String kind,String quote,ChatAuthorView author) {}
}
