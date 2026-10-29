/** 명세서의 사진 업로드 URL 발급 요청입니다. */
package com.dandimate.durooduroo.photo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UploadRequest(@NotEmpty List<@Valid FileRequest> files) {
    public record FileRequest(@NotBlank String clientFileId, @NotBlank String fileName,
                              @NotBlank String contentType, long sizeBytes, int displayOrder) {}
}
