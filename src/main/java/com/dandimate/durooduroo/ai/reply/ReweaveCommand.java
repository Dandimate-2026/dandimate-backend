/** 재엮기 입력: 기준 문단 버전, 현재 본문·인용, 새 답가와 분류값. */
package com.dandimate.durooduroo.ai.reply;

import com.dandimate.durooduroo.ai.common.AiInputs.Citation;
import java.util.List;

public record ReweaveCommand(String paragraphId, String photoId, int baseParagraphVersion, String body, List<Citation> citations,
                             ReplyInput reply, String classification) {

    public record ReplyInput(String id, String memberId, String nickname, String transcript) {}
}
