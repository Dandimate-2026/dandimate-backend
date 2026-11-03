/**
 * OpenAI 연동 전까지 쓰는 장면 카드 구현입니다 (app.ai.provider=rule, 기본값).
 * 원문을 그대로 voices로 옮기고, 꼭 인용할 원문은 덧붙임이 있으면 첫 덧붙임, 없으면 첫 원문으로 정한다.
 */
package com.dandimate.durooduroo.ai.scene;

import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.Safety;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "rule", matchIfMissing = true)
public class QuoteSceneCardAiService implements SceneCardAiService {

    @Override
    public SceneCardResult extract(SceneCardCommand command) {
        List<EntryInput> entries = command.entries();
        List<SceneCardResult.Voice> voices = entries.stream()
                .map(e -> new SceneCardResult.Voice(e.id(), e.memberId(), e.transcript(), null)).toList();
        String mustQuote = entries.stream().filter(e -> e.kind().equals("ADDON")).findFirst()
                .or(() -> entries.stream().findFirst()).map(EntryInput::id).orElse(null);
        String what = entries.stream().filter(e -> e.kind().equals("FIRST")).findFirst().map(EntryInput::transcript).orElse(null);
        return new SceneCardResult("1.0", command.photo().id(), what, entries.stream().map(EntryInput::memberId).distinct().toList(),
                List.of(), voices, null, null, mustQuote, Safety.allow(), "rule", "scene-card-rule-v1", null);
    }
}
