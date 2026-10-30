/** 텍스트·음성 변환 결과·패스 요청을 받는 DTO입니다. */
package com.dandimate.durooduroo.memory.dto;
public record MemoryEntryRequest(String kind, String inputMode, String transcriptionId,
                                 String transcript, String content, boolean skip) {
    public String text() { return transcript != null ? transcript : content; }
}
