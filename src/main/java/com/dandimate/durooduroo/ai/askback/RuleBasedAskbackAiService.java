/**
 * OpenAI 연동 전까지 쓰는 규칙 기반 되묻기 구현입니다 (app.ai.provider=rule, 기본값).
 * 우선순위: NO_ADDON(허용됐을 때) → MISSING_REASON(원문이 하나) → MISSING_CONTEXT. CONFLICT는 판정하지 않는다.
 * 대상은 사진을 올린 사람이 허용 목록에 있으면 그 사람, 아니면 허용 목록의 첫 사람.
 */
package com.dandimate.durooduroo.ai.askback;

import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.MemberInput;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "rule", matchIfMissing = true)
public class RuleBasedAskbackAiService implements AskbackAiService {

    private static final String MODEL = "rule";
    private static final String PROMPT_VERSION = "askback-rule-v1";

    @Override
    public AskbackResult analyze(AskbackCommand command) {
        List<String> eligible = command.limits().eligibleMemberIds();
        List<String> allowed = command.limits().allowedGapTypes();
        if (eligible.isEmpty() || command.entries().isEmpty()) return AskbackResult.noAsk("질문할 대상이나 원문이 없음", MODEL, PROMPT_VERSION);

        String uploader = command.photo().uploaderMemberId();
        String target = eligible.contains(uploader) ? uploader : eligible.get(0);
        String nickname = command.members().stream().filter(m -> m.id().equals(target)).map(MemberInput::nickname).findFirst().orElse("");
        List<String> evidence = command.entries().stream().map(EntryInput::id).toList();

        if (allowed.contains("NO_ADDON")) {
            return ask("NO_ADDON", 0.8, target, nickname + " 님, 이 사진에서 꼭 남기고 싶은 기억은 뭐예요?", evidence, "덧붙임이 없고 첫 한마디가 짧음");
        }
        if (allowed.contains("MISSING_REASON") && command.entries().size() == 1) {
            return ask("MISSING_REASON", 0.7, target, nickname + " 님, 그 순간이 특별했던 이유가 뭐였어요?", evidence, "이유가 드러나지 않음");
        }
        if (allowed.contains("MISSING_CONTEXT") && command.entries().size() <= 2) {
            return ask("MISSING_CONTEXT", 0.65, target, nickname + " 님, 이 사진 앞뒤로 무슨 일이 있었어요?", evidence, "앞뒤 맥락이 없음");
        }
        return AskbackResult.noAsk("충분한 이야기가 있음", MODEL, PROMPT_VERSION);
    }

    private AskbackResult ask(String gapType, double confidence, String target, String question, List<String> evidence, String reason) {
        return new AskbackResult("1.0", true, gapType, confidence, target, question, evidence, reason, MODEL, PROMPT_VERSION, null);
    }
}
