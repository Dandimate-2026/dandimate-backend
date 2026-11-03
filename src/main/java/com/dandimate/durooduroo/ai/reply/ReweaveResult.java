/** 재엮기 결과: 8장 paragraphs[] 한 개와 같은 형태. */
package com.dandimate.durooduroo.ai.reply;

import com.dandimate.durooduroo.ai.weave.ScrollWeaveResult.WovenParagraph;

public record ReweaveResult(String schemaVersion, WovenParagraph paragraph, String model, String promptVersion, String openaiRequestId) {
}
