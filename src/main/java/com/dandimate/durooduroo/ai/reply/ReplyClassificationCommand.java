/** 답가 분류 입력: 현재 문단 본문과 새 답가. */
package com.dandimate.durooduroo.ai.reply;

public record ReplyClassificationCommand(String paragraphBody, String replyId, String nickname, String transcript) {
}
