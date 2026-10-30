/** OpenAPI 명세서 기준 방 생성 요청입니다. */
package com.dandimate.durooduroo.room.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SpecCreateRoomRequest(@NotBlank @Size(max = 20) String name, @NotNull LocalDate meetingDate,
                                    @NotBlank @Size(max = 10) String hostNickname,
                                    @NotNull @Valid ConsentRequest consent) {}
