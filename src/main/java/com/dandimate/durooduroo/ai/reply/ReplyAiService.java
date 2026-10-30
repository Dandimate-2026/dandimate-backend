/** 답가 분류 계약 (명세 9장). */
package com.dandimate.durooduroo.ai.reply;

public interface ReplyAiService {
    ReplyClassificationResult classify(ReplyClassificationCommand command);
}
