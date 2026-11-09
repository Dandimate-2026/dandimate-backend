/** OpenAPI 명세서 기준 방 생성 요청입니다. */
package com.dandimate.durooduroo.room.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SpecCreateRoomRequest(@NotBlank @Size(max = 20) String name, @NotNull LocalDate meetingDate,
                                    @NotBlank @Size(max = 10) String hostNickname,
                                    @NotNull @Valid ConsentRequest consent,
                                    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(8) Integer capacity,
                                    @NotNull com.dandimate.durooduroo.room.domain.RoomPlan planType,
                                    @Valid RoomPlace place, @Valid CoverPhotoRef coverPhoto, String coverImageUrl,
                                    OffsetDateTime deadlineAt) {
    public CoverPhotoRef selectedCover() {
        CoverPhotoRef fromUrl = CoverPhotoRef.fromUrl(coverImageUrl);
        if (fromUrl != null && coverPhoto != null && !fromUrl.id().equals(coverPhoto.id()))
            throw new com.dandimate.durooduroo.common.exception.BusinessException(
                com.dandimate.durooduroo.common.exception.ErrorCode.VALIDATION_ERROR, "대표 사진 값이 서로 다릅니다.");
        return fromUrl == null ? coverPhoto : fromUrl;
    }
    public int selectedCapacity() { return capacity == null ? 8 : capacity; }
}
