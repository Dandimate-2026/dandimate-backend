package com.dandimate.durooduroo.ai.chat;
import com.dandimate.durooduroo.ai.common.AiFailure;
import com.dandimate.durooduroo.ai.openai.OpenAiResponsesClient;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
@Service
public class OpenAiChatService implements ChatAiService {
    private final ObjectProvider<OpenAiResponsesClient> clients;
    public OpenAiChatService(ObjectProvider<OpenAiResponsesClient> clients) {this.clients=clients;}
    public ChatResult answer(ChatCommand command) {
        var client=clients.getIfAvailable();
        if(client==null)throw new AiFailure(AiFailure.Code.NOT_CONFIGURED,false,"Chat model is not configured");
        var schema=Map.<String,Object>of("type","object","additionalProperties",false,
            "required",List.of("answer","citedEntryIds"),
            "properties",Map.of("answer",Map.of("type","string"),"citedEntryIds",Map.of("type","array","items",Map.of("type","string"))));
        var output=client.call("chat-v1","memory_chat",schema,command).output();
        if(!output.path("answer").isTextual()||!output.path("citedEntryIds").isArray())
            throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,true,"Invalid chat output");
        List<String> ids=new ArrayList<>();
        for(var id:output.path("citedEntryIds")) {
            if(!id.isTextual())throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,true,"Invalid citation");
            ids.add(id.asText());
        }
        return new ChatResult(output.path("answer").asText(),ids);
    }
}
