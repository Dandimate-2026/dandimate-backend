/** 로컬 저장소 사진을 서명된 URL로 내려준다. 서명이 틀리거나 만료되면 403. (S3는 presigned URL을 쓰므로 이 API를 거치지 않는다.) */
package com.dandimate.durooduroo.photo.controller;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.photo.service.PhotoUrls;
import com.dandimate.durooduroo.storage.ObjectStorage;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PhotoImageController {

    private final PhotoRepository photos;
    private final PhotoUrls photoUrls;
    private final ObjectStorage storage;

    public PhotoImageController(PhotoRepository photos, PhotoUrls photoUrls, ObjectStorage storage) {
        this.photos = photos;
        this.photoUrls = photoUrls;
        this.storage = storage;
    }

    @GetMapping("/api/v1/photos/{photoId}/image")
    public ResponseEntity<Resource> image(@PathVariable String photoId, @RequestParam long expires, @RequestParam String signature) {
        if (!photoUrls.verifyImage(photoId, expires, signature)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "사진 주소가 만료됐어요. 목록을 다시 불러와 주세요.");
        }
        var photo = photos.findById(photoId).filter(p -> p.isReady())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다."));
        Resource file = storage.open(photo.getFileUrl())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다."));
        MediaType type = MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate()).body(file);
    }
}
