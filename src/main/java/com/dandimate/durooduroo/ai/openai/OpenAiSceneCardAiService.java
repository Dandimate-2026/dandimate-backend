/**
 * OpenAI 장면 카드 구현 (app.ai.provider=openai). 지금은 원문 텍스트만 보낸다.
 * 사진을 함께 보내려면 OpenAI가 받을 수 있는 서명 URL(S3 presigned)을 PhotoInput.imageUrl에 넣고 input_image로 추가한다.
 */
package com.dandimate.durooduroo.ai.openai;

import static com.dandimate.durooduroo.ai.openai.Schemas.*;

import com.dandimate.durooduroo.ai.common.AiInputs.Safety;
import com.dandimate.durooduroo.ai.scene.SceneCardAiService;
import com.dandimate.durooduroo.ai.scene.SceneCardCommand;
import com.dandimate.durooduroo.ai.scene.SceneCardResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiSceneCardAiService implements SceneCardAiService {

    static final String PROMPT_VERSION = "scene-card-v1";
    private static final Map<String, Object> CLAIM = object(ordered("entryId", string(), "memberId", string(), "claim", string()));
    private static final Map<String, Object> SCHEMA = object(ordered(
            "what", nullableString(),
            "whoMemberIds", array(string()),
            "emotion", array(string()),
            "voices", array(object(ordered("entryId", string(), "memberId", string(), "exactQuote", string(), "toneNote", nullableString()))),
            "conflict", Map.of("anyOf", List.of(object(ordered("summary", string(), "claims", array(CLAIM))), Map.of("type", "null"))),
            "gap", nullableString(),
            "mustQuoteEntryId", nullableString(),
            "safety", safety()));

    private final OpenAiResponsesClient client;

    public OpenAiSceneCardAiService(OpenAiResponsesClient client) {
        this.client = client;
    }

    @Override
    public SceneCardResult extract(SceneCardCommand command) {
        var result = client.call(PROMPT_VERSION, "scene_card", SCHEMA, command);
        JsonNode out = result.output();
        List<SceneCardResult.Voice> voices = new ArrayList<>();
        out.path("voices").forEach(v -> voices.add(new SceneCardResult.Voice(v.path("entryId").asText(), v.path("memberId").asText(),
                v.path("exactQuote").asText(), OpenAiAskbackAiService.text(v, "toneNote"))));
        SceneCardResult.Conflict conflict = null;
        if (out.path("conflict").isObject()) {
            List<SceneCardResult.Claim> claims = new ArrayList<>();
            out.path("conflict").path("claims").forEach(c -> claims.add(new SceneCardResult.Claim(c.path("entryId").asText(), c.path("memberId").asText(), c.path("claim").asText())));
            conflict = new SceneCardResult.Conflict(out.path("conflict").path("summary").asText(), claims);
        }
        return new SceneCardResult("1.0", command.photo().id(), OpenAiAskbackAiService.text(out, "what"),
                OpenAiAskbackAiService.strings(out.path("whoMemberIds")), OpenAiAskbackAiService.strings(out.path("emotion")), voices, conflict,
                OpenAiAskbackAiService.text(out, "gap"), OpenAiAskbackAiService.text(out, "mustQuoteEntryId"), toSafety(out.path("safety")),
                result.model(), PROMPT_VERSION, result.requestId());
    }

    static Safety toSafety(JsonNode node) {
        return new Safety(node.path("action").asText("ALLOW"), OpenAiAskbackAiService.strings(node.path("categories")));
    }
}
