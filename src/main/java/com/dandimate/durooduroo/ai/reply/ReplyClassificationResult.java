/** 답가 분류: NEW_FACT / CORRECTION / REACTION_ONLY / UNSAFE. */
package com.dandimate.durooduroo.ai.reply;

public record ReplyClassificationResult(String classification, double confidence, String reason,
                                        String schemaVersion, String model, String promptVersion, String openaiRequestId) {
}
