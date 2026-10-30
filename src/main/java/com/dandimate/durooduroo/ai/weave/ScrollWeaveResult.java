/** 엮기 결과. 문단은 Spring이 인용·사진 수를 검증한 뒤에만 저장한다. */
package com.dandimate.durooduroo.ai.weave;

import com.dandimate.durooduroo.ai.common.AiInputs.Citation;
import com.dandimate.durooduroo.ai.common.AiInputs.Safety;
import java.util.List;

public record ScrollWeaveResult(String schemaVersion, List<WovenParagraph> paragraphs, String model, String promptVersion, String openaiRequestId) {

    public record WovenParagraph(String photoId, String body, List<Citation> citations, Safety safety) {}
}
