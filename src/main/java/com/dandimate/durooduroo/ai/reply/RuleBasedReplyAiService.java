/**
 * OpenAI 연동 전까지 쓰는 답가 분류·재엮기 구현입니다 (app.ai.provider=rule, 기본값).
 * - "ㅋㅋ", "맞아" 같은 짧은 반응은 REACTION_ONLY, 금칙어는 UNSAFE, "사실은/아니야/정정"은 CORRECTION, 나머지는 NEW_FACT.
 * - 재엮기는 기존 문장과 인용을 그대로 두고 뒤에 한 문장을 붙인다. 정정은 기존 사실을 지우지 않고 "~라고 정정했다"로 병기한다.
 */
package com.dandimate.durooduroo.ai.reply;

import com.dandimate.durooduroo.ai.common.AiInputs.Citation;
import com.dandimate.durooduroo.ai.common.AiInputs.Safety;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveResult.WovenParagraph;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "rule", matchIfMissing = true)
public class RuleBasedReplyAiService implements ReplyAiService, ParagraphReweaveAiService {

    private static final Pattern REACTION = Pattern.compile("^[ㅋㅎㅠㅜ!?~. ]+$|^(맞아|맞다|맞네|그치|그러게|대박|헐|와|ㄹㅇ|인정)[!?~. ㅋㅎ]*$");
    private static final List<String> UNSAFE_WORDS = List.of("씨발", "병신", "개새");
    private static final List<String> CORRECTION_HINTS = List.of("사실은", "사실 ", "아니야", "아니고", "정정", "잘못");

    @Override
    public ReplyClassificationResult classify(ReplyClassificationCommand command) {
        String text = command.transcript().strip();
        if (UNSAFE_WORDS.stream().anyMatch(text::contains)) return result("UNSAFE", 0.9, "금칙어 포함");
        if (REACTION.matcher(text).matches()) return result("REACTION_ONLY", 0.9, "반응만 있음");
        if (CORRECTION_HINTS.stream().anyMatch(text::contains)) return result("CORRECTION", 0.7, "기존 내용을 바로잡음");
        return result("NEW_FACT", 0.7, "새 사실");
    }

    @Override
    public ReweaveResult reweave(ReweaveCommand command) {
        var reply = command.reply();
        String verb = "CORRECTION".equals(command.classification()) ? "정정했다" : "덧붙였다";
        String body = command.body() + " " + reply.nickname() + "은(는) ‘" + reply.transcript().strip() + "’라고 " + verb + ".";
        List<Citation> citations = new ArrayList<>(command.citations());
        citations.add(new Citation(reply.id(), reply.memberId(), reply.transcript().strip()));
        return new ReweaveResult("1.0", new WovenParagraph(command.photoId(), body, citations, Safety.allow()), "rule", "reweave-rule-v1", null);
    }

    private ReplyClassificationResult result(String classification, double confidence, String reason) {
        return new ReplyClassificationResult(classification, confidence, reason, "1.0", "rule", "reply-classify-rule-v1", null);
    }
}
