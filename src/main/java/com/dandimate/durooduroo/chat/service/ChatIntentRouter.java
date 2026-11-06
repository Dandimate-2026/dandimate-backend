package com.dandimate.durooduroo.chat.service;

import com.dandimate.durooduroo.ai.common.AiFailure;
import com.dandimate.durooduroo.ai.openai.OpenAiResponsesClient;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Receives no user records or identities. Ambiguous requests go through authorized retrieval. */
@Service
public class ChatIntentRouter {
    public record Route(String kind, String reply) {}
    private final ObjectProvider<OpenAiResponsesClient> clients;
    public ChatIntentRouter(ObjectProvider<OpenAiResponsesClient> clients) { this.clients = clients; }

    public Route route(String question, boolean hasConversation) {
        var client = clients.getIfAvailable();
        if (client == null) return new Route("RECORD", "");
        var schema = Map.<String,Object>of("type", "object", "additionalProperties", false,
                "required", List.of("kind", "reply"), "properties", Map.of(
                "kind", Map.of("type", "string", "enum", List.of("RECORD", "SMALL_TALK", "OUT_OF_SCOPE")),
                "reply", Map.of("type", "string", "maxLength", 350)));
        var result = client.call("chat-route-v1", "chat_route", schema,
                Map.of("question", question, "hasConversation", hasConversation)).output();
        String kind = result.path("kind").asText(), reply = result.path("reply").asText();
        if (!List.of("RECORD", "SMALL_TALK", "OUT_OF_SCOPE").contains(kind)
                || (!kind.equals("RECORD") && (reply.isBlank() || reply.length() > 350)))
            throw new AiFailure(AiFailure.Code.INVALID_OUTPUT, true, "Invalid chat route");
        return new Route(kind, reply);
    }
}
