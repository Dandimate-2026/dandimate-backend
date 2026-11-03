/** 초대 토큰 입장 API의 요청 형식입니다. */
package com.dandimate.durooduroo.member.dto;

import com.dandimate.durooduroo.room.dto.ConsentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SpecJoinRoomRequest(@NotBlank @Size(max = 10) String nickname,
                                  @NotNull @Valid ConsentRequest consent) {}
