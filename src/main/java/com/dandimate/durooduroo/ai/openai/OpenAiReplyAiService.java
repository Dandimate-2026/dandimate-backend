/** OpenAI 답가 분류·부분 재엮기 구현 (app.ai.provider=openai). 재엮기 결과는 ReplyReweaveService가 검증한다. */
package com.dandimate.durooduroo.ai.openai;

import static com.dandimate.durooduroo.ai.openai.Schemas.*;

import com.dandimate.durooduroo.ai.reply.ParagraphReweaveAiService;
import com.dandimate.durooduroo.ai.reply.ReplyAiService;
import com.dandimate.durooduroo.ai.reply.ReplyClassificationCommand;
import com.dandimate.durooduroo.ai.reply.ReplyClassificationResult;
import com.dandimate.durooduroo.ai.reply.ReweaveCommand;
import com.dandimate.durooduroo.ai.reply.ReweaveResult;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiReplyAiService implements ReplyAiService, ParagraphReweaveAiService {

    static final String CLASSIFY_PROMPT = "reply-classify-v1";
    static final String REWEAVE_PROMPT = "reweave-v1";
    private static final Map<String, Object> CLASSIFY_SCHEMA = object(ordered(
            "classification", enumOf("NEW_FACT", "CORRECTION", "REACTION_ONLY", "UNSAFE"), "confidence", number(), "reason", string()));

    private final OpenAiResponsesClient client;

    public OpenAiReplyAiService(OpenAiResponsesClient client) {
        this.client = client;
    }

    @Override
    public ReplyClassificationResult classify(ReplyClassificationCommand command) {
        var result = client.call(CLASSIFY_PROMPT, "reply_classification", CLASSIFY_SCHEMA, command);
        var out = result.output();
        return new ReplyClassificationResult(out.path("classification").asText(), out.path("confidence").asDouble(0), out.path("reason").asText(""),
                "1.0", result.model(), CLASSIFY_PROMPT, result.requestId());
    }

    @Override
    public ReweaveResult reweave(ReweaveCommand command) {
        var result = client.call(REWEAVE_PROMPT, "paragraph_reweave", OpenAiScrollWeaveAiService.PARAGRAPH, command);
        return new ReweaveResult("1.0", OpenAiScrollWeaveAiService.paragraph(result.output()), result.model(), REWEAVE_PROMPT, result.requestId());
    }
}
