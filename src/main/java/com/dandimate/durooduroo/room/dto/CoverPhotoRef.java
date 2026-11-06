package com.dandimate.durooduroo.room.dto;
import jakarta.validation.constraints.*;
public record CoverPhotoRef(@NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") String id) {
    public static CoverPhotoRef fromUrl(String url) {
        if (url == null) return null;
        if (!url.matches("/api/v1/room-covers/[0-9a-fA-F-]{36}/image"))
            throw new com.dandimate.durooduroo.common.exception.BusinessException(
                com.dandimate.durooduroo.common.exception.ErrorCode.VALIDATION_ERROR,
                "대표 사진 업로드 API가 반환한 imageUrl을 보내 주세요.");
        String id = url.substring("/api/v1/room-covers/".length(), url.length()-"/image".length());
        try { java.util.UUID.fromString(id); } catch (IllegalArgumentException ex) {
            throw new com.dandimate.durooduroo.common.exception.BusinessException(
                com.dandimate.durooduroo.common.exception.ErrorCode.VALIDATION_ERROR, "잘못된 대표 사진 주소입니다.");
        }
        return new CoverPhotoRef(id);
    }
}
