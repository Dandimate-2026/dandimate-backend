/** OpenAI 두루마리 엮기 구현 (app.ai.provider=openai). 결과는 ScrollGenerationService가 인용을 검증한다. */
package com.dandimate.durooduroo.ai.openai;

import static com.dandimate.durooduroo.ai.openai.Schemas.*;

import com.dandimate.durooduroo.ai.common.AiInputs.Citation;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveAiService;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveCommand;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiScrollWeaveAiService implements ScrollWeaveAiService {

    static final String PROMPT_VERSION = "weave-v1";
    static final Map<String, Object> PARAGRAPH = object(ordered("photoId", string(), "body", string(), "citations", array(citation()), "safety", safety()));
    private static final Map<String, Object> SCHEMA = object(ordered("paragraphs", array(PARAGRAPH)));

    private final OpenAiResponsesClient client;

    public OpenAiScrollWeaveAiService(OpenAiResponsesClient client) {
        this.client = client;
    }

    @Override
    public ScrollWeaveResult weave(ScrollWeaveCommand command) {
        var result = client.call(PROMPT_VERSION, "scroll_weave", SCHEMA, command);
        List<ScrollWeaveResult.WovenParagraph> paragraphs = new ArrayList<>();
        result.output().path("paragraphs").forEach(p -> paragraphs.add(paragraph(p)));
        return new ScrollWeaveResult("1.0", paragraphs, result.model(), PROMPT_VERSION, result.requestId());
    }

    static ScrollWeaveResult.WovenParagraph paragraph(JsonNode p) {
        List<Citation> citations = new ArrayList<>();
        p.path("citations").forEach(c -> citations.add(new Citation(c.path("entryId").asText(), c.path("memberId").asText(), c.path("exactQuote").asText())));
        return new ScrollWeaveResult.WovenParagraph(p.path("photoId").asText(), p.path("body").asText(), citations, OpenAiSceneCardAiService.toSafety(p.path("safety")));
    }
}
