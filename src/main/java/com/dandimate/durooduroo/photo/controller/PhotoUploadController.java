/** 명세서 기준 사진 업로드 URL 발급·(로컬) 업로드·업로드 완료 API입니다. */
package com.dandimate.durooduroo.photo.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.photo.dto.UploadRequest;
import com.dandimate.durooduroo.photo.dto.UploadResponse;
import com.dandimate.durooduroo.photo.service.PhotoUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PhotoUploadController {

    private final PhotoUploadService uploads;

    public PhotoUploadController(PhotoUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping("/rooms/{roomId}/photos/upload-requests")
    public ResponseEntity<ApiResponse<UploadResponse>> requestUploads(@PathVariable String roomId, @RequestHeader(required = false) String authorization,
                                                                      @Valid @RequestBody UploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(uploads.requestUploads(roomId, authorization, request)));
    }

    /** uploadUrl이 가리키는 로컬 저장소 업로드. 본문은 파일 바이트 그대로이고 Content-Type은 업로드 요청 때와 같아야 한다. */
    @PutMapping("/photos/{photoId}/upload")
    public ResponseEntity<Void> upload(@PathVariable String photoId, @RequestParam long expires, @RequestParam String signature,
                                       HttpServletRequest request) throws IOException {
        uploads.receiveLocalUpload(photoId, expires, signature, request.getContentType(), request.getInputStream());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/photos/{photoId}/complete")
    public ApiResponse<?> complete(@PathVariable String photoId, @RequestHeader(required = false) String authorization) {
        var photo = uploads.complete(photoId, authorization);
        return ApiResponse.success(Map.of("photo", Map.of("id", photo.getId(), "status", photo.getStatus(), "displayOrder", photo.getDisplayOrder())));
    }
}
