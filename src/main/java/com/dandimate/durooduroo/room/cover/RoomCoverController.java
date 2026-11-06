package com.dandimate.durooduroo.room.cover;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.exception.*;
import com.dandimate.durooduroo.room.dto.CoverPhotoView;
import com.dandimate.durooduroo.storage.ObjectStorage;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
@RestController @RequestMapping("/api/v1/room-covers")
public class RoomCoverController {
    private final RoomCoverService covers;private final ObjectStorage storage;
    public RoomCoverController(RoomCoverService covers,ObjectStorage storage){this.covers=covers;this.storage=storage;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoverPhotoView> upload(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.success(covers.upload(file));
    }
    @GetMapping("/{id}/image")
    public ResponseEntity<?> image(@PathVariable String id) {
        var cover=covers.readable(id);
        var url=storage.presignedGetUrl(cover.getLocation(),Duration.ofMinutes(5));
        if(url.isPresent())return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url.get())).cacheControl(CacheControl.noStore()).build();
        var resource=storage.open(cover.getLocation()).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"사진 파일이 없습니다."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(cover.getContentType())).body(resource);
    }
}
