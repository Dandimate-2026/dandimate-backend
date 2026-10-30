/** OpenAPI CreateEntryRequest: 첫 한마디·덧붙임 확정 요청. 음성 입력이면 transcriptionId가 필요하다. */
package com.dandimate.durooduroo.memory.dto;

import com.dandimate.durooduroo.memory.domain.EntryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEntryRequest(
        @NotNull EntryKind kind,
        @NotNull @Pattern(regexp = "VOICE|TEXT", message = "inputMode는 VOICE 또는 TEXT여야 합니다.") String inputMode,
        String transcriptionId,
        @NotBlank @Size(max = 300) String transcript
) {
}
