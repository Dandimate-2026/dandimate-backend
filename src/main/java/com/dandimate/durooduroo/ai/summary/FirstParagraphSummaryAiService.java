/** OpenAI 연동 전까지 쓰는 요약 구현입니다 (app.ai.provider=rule, 기본값). 첫 문단 앞부분을 그대로 씁니다. */
package com.dandimate.durooduroo.ai.summary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "rule", matchIfMissing = true)
public class FirstParagraphSummaryAiService implements ScrollSummaryAiService {

    @Override
    public String summarize(ScrollSummaryCommand command) {
        String body = command.paragraphs().get(0).body().strip();
        return body.length() <= MAX_LENGTH ? body : body.substring(0, MAX_LENGTH - 1) + "…";
    }
}
