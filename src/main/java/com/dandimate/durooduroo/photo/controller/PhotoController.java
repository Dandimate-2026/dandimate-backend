/** 사진 업로드 HTTP API를 제공합니다. */
package com.dandimate.durooduroo.photo.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.photo.dto.PhotoResponse;
import com.dandimate.durooduroo.photo.service.PhotoService;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/rooms/{roomId}/photos")
public class PhotoController {
    private final PhotoService service; public PhotoController(PhotoService service){this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PhotoResponse>> upload(@PathVariable String roomId,@RequestParam String memberToken,@RequestPart MultipartFile file) throws Exception { return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.upload(roomId,memberToken,file))); }
}
