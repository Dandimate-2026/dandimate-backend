/** 명세서의 사진 업로드 URL 발급 응답입니다. */
package com.dandimate.durooduroo.photo.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UploadResponse(List<Upload> uploads) {
    public record Upload(String clientFileId, String photoId, String uploadUrl,
                         LocalDateTime expiresAt, java.util.Map<String, String> requiredHeaders) {}
}
