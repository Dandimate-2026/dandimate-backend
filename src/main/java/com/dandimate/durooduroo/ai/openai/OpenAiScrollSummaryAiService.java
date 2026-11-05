/** OpenAI 한 줄 요약 구현 (app.ai.provider=openai). */
package com.dandimate.durooduroo.ai.openai;

import static com.dandimate.durooduroo.ai.openai.Schemas.*;

import com.dandimate.durooduroo.ai.summary.ScrollSummaryAiService;
import com.dandimate.durooduroo.ai.summary.ScrollSummaryCommand;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiScrollSummaryAiService implements ScrollSummaryAiService {

    static final String PROMPT_VERSION = "scroll-summary-v1";
    private static final Map<String, Object> SCHEMA = object(ordered("summary", string()));

    private final OpenAiResponsesClient client;

    public OpenAiScrollSummaryAiService(OpenAiResponsesClient client) {
        this.client = client;
    }

    @Override
    public String summarize(ScrollSummaryCommand command) {
        return client.call(PROMPT_VERSION, "scroll_summary", SCHEMA, command).output().path("summary").asText();
    }
}
