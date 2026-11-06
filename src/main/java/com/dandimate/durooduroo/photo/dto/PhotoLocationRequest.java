/** 사진 장소 입력 요청입니다. 좌표는 장소 검색 결과에서 고른 경우에만 보냅니다. */
package com.dandimate.durooduroo.photo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhotoLocationRequest(@NotBlank @Size(max = 60) String placeName, @Size(max = 200) String address,
                                   @DecimalMin("-90") @DecimalMax("90") Double latitude,
                                   @DecimalMin("-180") @DecimalMax("180") Double longitude, @Size(max = 100) String placeId) {
    public PhotoLocationRequest(String name,String address,Double lat,Double lng) { this(name,address,lat,lng,null); }
}
