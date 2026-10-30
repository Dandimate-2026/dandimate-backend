/** OpenAI 되묻기 구현 (app.ai.provider=openai). 결과는 AskbackAnalysisService가 다시 검증한다. */
package com.dandimate.durooduroo.ai.openai;

import static com.dandimate.durooduroo.ai.openai.Schemas.*;

import com.dandimate.durooduroo.ai.askback.AskbackAiService;
import com.dandimate.durooduroo.ai.askback.AskbackCommand;
import com.dandimate.durooduroo.ai.askback.AskbackResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiAskbackAiService implements AskbackAiService {

    static final String PROMPT_VERSION = "askback-v1";
    private static final java.util.Map<String, Object> SCHEMA = object(ordered(
            "shouldAsk", bool(),
            "gapType", nullableEnumOf("CONFLICT", "MISSING_REASON", "NO_ADDON", "MISSING_CONTEXT"),
            "confidence", number(),
            "targetMemberId", nullableString(),
            "question", nullableString(),
            "evidenceEntryIds", array(string()),
            "reason", string()));

    private final OpenAiResponsesClient client;

    public OpenAiAskbackAiService(OpenAiResponsesClient client) {
        this.client = client;
    }

    @Override
    public AskbackResult analyze(AskbackCommand command) {
        var result = client.call(PROMPT_VERSION, "askback_result", SCHEMA, command);
        JsonNode out = result.output();
        return new AskbackResult("1.0", out.path("shouldAsk").asBoolean(false), text(out, "gapType"), out.path("confidence").asDouble(0),
                text(out, "targetMemberId"), text(out, "question"), strings(out.path("evidenceEntryIds")), out.path("reason").asText(""),
                result.model(), PROMPT_VERSION, result.requestId());
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asText()));
        return values;
    }
}
