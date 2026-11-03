/** 되묻기 분석 결과. shouldAsk=false도 정상 결과다. */
package com.dandimate.durooduroo.ai.askback;

import java.util.List;

public record AskbackResult(String schemaVersion, boolean shouldAsk, String gapType, double confidence, String targetMemberId,
                            String question, List<String> evidenceEntryIds, String reason,
                            String model, String promptVersion, String openaiRequestId) {

    public static AskbackResult noAsk(String reason, String model, String promptVersion) {
        return new AskbackResult("1.0", false, null, 0, null, null, List.of(), reason, model, promptVersion, null);
    }
}
