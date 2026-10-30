/**
 * OpenAI 연동 전까지 쓰는 엮기 구현입니다 (app.ai.provider=rule, 기본값).
 * 장면 카드의 목소리를 순서대로 "닉네임은(는) ‘원문’라고 기억했다."로 잇고, 각 원문을 그대로 인용한다.
 */
package com.dandimate.durooduroo.ai.weave;

import com.dandimate.durooduroo.ai.common.AiInputs.Citation;
import com.dandimate.durooduroo.ai.common.AiInputs.Safety;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "rule", matchIfMissing = true)
public class QuoteScrollWeaveAiService implements ScrollWeaveAiService {

    @Override
    public ScrollWeaveResult weave(ScrollWeaveCommand command) {
        List<ScrollWeaveResult.WovenParagraph> paragraphs = command.sceneCards().stream()
                .sorted(Comparator.comparingInt(ScrollWeaveCommand.OrderedSceneCard::displayOrder))
                .map(ordered -> {
                    var card = ordered.card();
                    List<Citation> citations = card.voices().stream().map(v -> new Citation(v.entryId(), v.memberId(), v.exactQuote())).toList();
                    String body = card.voices().stream()
                            .map(v -> command.sourceEntries().get(v.entryId()).nickname() + "은(는) ‘" + v.exactQuote() + "’라고 기억했다.")
                            .collect(Collectors.joining(" "));
                    return new ScrollWeaveResult.WovenParagraph(card.photoId(), body, citations, Safety.allow());
                }).toList();
        return new ScrollWeaveResult("1.0", paragraphs, "rule", "weave-rule-v1", null);
    }
}
