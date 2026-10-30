/** 약관·미디어·외부 AI 처리 동의 정보입니다. */
package com.dandimate.durooduroo.room.dto;

import jakarta.validation.constraints.NotBlank;

public record ConsentRequest(@NotBlank String version, boolean terms, boolean mediaProcessing, boolean externalAiProcessing) {
    public void validate() {
        if (!terms || !mediaProcessing || !externalAiProcessing) {
            throw new IllegalArgumentException("필수 동의 항목에 동의해야 합니다.");
        }
    }
}
