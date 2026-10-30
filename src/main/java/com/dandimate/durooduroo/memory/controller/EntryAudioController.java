/** 음성 입력 원문의 음성을 서명된 URL로 내려준다 (로컬 저장소용. S3는 presigned URL을 쓴다). */
package com.dandimate.durooduroo.memory.controller;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.storage.MediaUrls;
import com.dandimate.durooduroo.storage.ObjectStorage;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class EntryAudioController {

    private final MemoryEntryRepository entries;
    private final MediaUrls mediaUrls;
    private final ObjectStorage storage;

    public EntryAudioController(MemoryEntryRepository entries, MediaUrls mediaUrls, ObjectStorage storage) {
        this.entries = entries;
        this.mediaUrls = mediaUrls;
        this.storage = storage;
    }

    @GetMapping("/api/v1/entries/{entryId}/audio")
    public ResponseEntity<Resource> audio(@PathVariable String entryId, @RequestParam long expires, @RequestParam String signature) {
        if (!mediaUrls.verifyAudio(entryId, expires, signature)) throw new BusinessException(ErrorCode.FORBIDDEN, "음성 주소가 만료됐어요. 다시 불러와 주세요.");
        var entry = entries.findById(entryId).filter(e -> e.getAudioLocation() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "음성이 없습니다."));
        Resource file = storage.open(entry.getAudioLocation()).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "음성이 없습니다."));
        MediaType type = MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate()).body(file);
    }
}
