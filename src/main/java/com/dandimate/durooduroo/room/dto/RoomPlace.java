package com.dandimate.durooduroo.room.dto;
import jakarta.validation.constraints.*;
public record RoomPlace(@NotBlank @Size(max=200) String placeName,
                        @NotNull @Size(max=500) String address,
                        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
                        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
