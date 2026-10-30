/** 장면 카드. voices.exactQuote는 원문 transcript의 정확한 부분 문자열이어야 한다. */
package com.dandimate.durooduroo.ai.scene;

import com.dandimate.durooduroo.ai.common.AiInputs.Safety;
import java.util.List;

public record SceneCardResult(String schemaVersion, String photoId, String what, List<String> whoMemberIds, List<String> emotion,
                              List<Voice> voices, Conflict conflict, String gap, String mustQuoteEntryId, Safety safety,
                              String model, String promptVersion, String openaiRequestId) {

    public record Voice(String entryId, String memberId, String exactQuote, String toneNote) {}

    public record Conflict(String summary, List<Claim> claims) {}

    public record Claim(String entryId, String memberId, String claim) {}
}
