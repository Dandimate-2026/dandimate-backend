/** AI 되묻기 계약 (명세 6장). 결과는 Spring이 다시 검증한 뒤에만 저장한다. */
package com.dandimate.durooduroo.ai.askback;

public interface AskbackAiService {
    AskbackResult analyze(AskbackCommand command);
}
