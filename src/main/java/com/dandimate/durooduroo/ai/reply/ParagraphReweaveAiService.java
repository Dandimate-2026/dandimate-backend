/** 문단 부분 재엮기 계약 (명세 9장). 결과는 8장 결과 검증을 그대로 거친다. */
package com.dandimate.durooduroo.ai.reply;

public interface ParagraphReweaveAiService {
    ReweaveResult reweave(ReweaveCommand command);
}
