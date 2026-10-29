/** 참여자 입장 API가 받는 닉네임 입력 형식이다. */
package com.dandimate.durooduroo.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 30, message = "닉네임은 30자 이내여야 합니다.")
        String nickname
) {
}
