/** 명세서 기준 원문 확정·수정·삭제, 첫 한마디 나중에, 패스 API입니다. */
package com.dandimate.durooduroo.memory.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.memory.dto.CreateEntryRequest;
import com.dandimate.durooduroo.memory.service.EntryService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EntryController {

    private final EntryService entryService;

    public EntryController(EntryService entryService) {
        this.entryService = entryService;
    }

    /** askbackJobId는 되묻기 Job을 붙이기 전까지 null이다. */
    @PostMapping("/photos/{photoId}/entries")
    public ResponseEntity<ApiResponse<?>> create(@PathVariable String photoId, @RequestHeader(required = false) String authorization,
                                                 @Valid @RequestBody CreateEntryRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("entry", entryService.create(photoId, authorization, request));
        data.put("askbackJobId", null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    @PatchMapping("/entries/{entryId}")
    public ApiResponse<?> edit(@PathVariable String entryId, @RequestHeader(required = false) String authorization,
                               @RequestBody Map<String, String> body) {
        return ApiResponse.success(Map.of("entry", entryService.edit(entryId, authorization, body.get("transcript"))));
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> delete(@PathVariable String entryId, @RequestHeader(required = false) String authorization) {
        entryService.delete(entryId, authorization);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/photos/{photoId}/first-entry/skip")
    public ApiResponse<?> skipFirstEntry(@PathVariable String photoId, @RequestHeader(required = false) String authorization) {
        var photo = entryService.skipFirstEntry(photoId, authorization);
        return ApiResponse.success(Map.of("photo", Map.of("id", photo.getId(), "published", photo.isPublished())));
    }

    @PutMapping("/photos/{photoId}/passes/me")
    public ApiResponse<?> pass(@PathVariable String photoId, @RequestHeader(required = false) String authorization) {
        entryService.pass(photoId, authorization);
        return ApiResponse.success(Map.of("myResponse", Map.of("status", "PASSED")));
    }

    @DeleteMapping("/photos/{photoId}/passes/me")
    public ResponseEntity<Void> cancelPass(@PathVariable String photoId, @RequestHeader(required = false) String authorization) {
        entryService.cancelPass(photoId, authorization);
        return ResponseEntity.noContent().build();
    }
}
