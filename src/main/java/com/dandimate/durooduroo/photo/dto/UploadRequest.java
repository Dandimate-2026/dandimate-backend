/** 명세서의 사진 업로드 URL 발급 요청입니다. */
package com.dandimate.durooduroo.photo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UploadRequest(@NotEmpty @Size(max = 3) List<@Valid FileRequest> files) {
    public record FileRequest(@NotBlank @Size(max = 64) String clientFileId, @NotBlank @Size(max = 255) String fileName,
                              @NotBlank @Pattern(regexp = "image/(jpeg|png|heic|heif)", message = "JPEG, PNG, HEIC/HEIF만 올릴 수 있어요.") String contentType,
                              @Min(1) @Max(2_000_000) long sizeBytes, @Min(1) @Max(3) int displayOrder) {}
}
