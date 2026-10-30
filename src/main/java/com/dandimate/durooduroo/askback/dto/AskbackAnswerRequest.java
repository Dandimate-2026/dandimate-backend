/** OpenAPI AskbackAnswerRequest: 되묻기 답변. 음성 입력이면 transcriptionId가 필요하다. */
package com.dandimate.durooduroo.askback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AskbackAnswerRequest(
        @NotNull @Pattern(regexp = "VOICE|TEXT", message = "inputMode는 VOICE 또는 TEXT여야 합니다.") String inputMode,
        String transcriptionId,
        @NotBlank @Size(max = 200) String transcript
) {
}
