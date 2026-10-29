/** 방 생성 API가 받는 입력 형식이다. */
package com.dandimate.durooduroo.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank(message = "방 제목은 필수입니다.")
        @Size(max = 100, message = "방 제목은 100자 이내여야 합니다.")
        String title
) {
}
