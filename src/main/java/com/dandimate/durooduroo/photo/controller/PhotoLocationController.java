/** 사진 장소 입력·삭제, 장소 검색, 내 지도 API입니다. */
package com.dandimate.durooduroo.photo.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.photo.dto.PhotoLocationRequest;
import com.dandimate.durooduroo.photo.dto.PhotoLocationView;
import com.dandimate.durooduroo.photo.dto.PhotoMapView;
import com.dandimate.durooduroo.photo.dto.PlaceView;
import com.dandimate.durooduroo.photo.service.KakaoPlaceSearch;
import com.dandimate.durooduroo.photo.service.PhotoLocationService;
import com.dandimate.durooduroo.photo.service.PhotoMapService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PhotoLocationController {
    private final PhotoLocationService locations;
    private final PhotoMapService maps;
    private final KakaoPlaceSearch places;

    public PhotoLocationController(PhotoLocationService locations, PhotoMapService maps, KakaoPlaceSearch places) {
        this.locations = locations;
        this.maps = maps;
        this.places = places;
    }

    @GetMapping("/places/search")
    public ApiResponse<List<PlaceView>> search(@RequestParam String query) {
        return ApiResponse.success(places.search(query));
    }

    @PutMapping("/photos/{photoId}/location")
    public ApiResponse<PhotoLocationView> set(@PathVariable String photoId, @RequestHeader(required = false) String authorization,
                                              @Valid @RequestBody PhotoLocationRequest request) {
        return ApiResponse.success(locations.set(photoId, authorization, request));
    }

    @DeleteMapping("/photos/{photoId}/location")
    public ResponseEntity<Void> clear(@PathVariable String photoId, @RequestHeader(required = false) String authorization) {
        locations.clear(photoId, authorization);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/map")
    public ApiResponse<PhotoMapView> map(@RequestParam(required = false) String roomId) {
        return ApiResponse.success(maps.map(CurrentUser.requireId(), roomId));
    }
}
