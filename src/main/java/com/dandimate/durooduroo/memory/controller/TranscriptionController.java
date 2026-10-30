/** 명세서 기준 음성 받아쓰기 초안 API입니다 (사진·되묻기 답변·답가). */
package com.dandimate.durooduroo.memory.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.memory.service.TranscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class TranscriptionController {

    private final TranscriptionService transcriptions;

    public TranscriptionController(TranscriptionService transcriptions) {
        this.transcriptions = transcriptions;
    }

    @PostMapping(value = "/photos/{photoId}/transcriptions", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TranscriptionService.DraftView> photo(@PathVariable String photoId, @RequestHeader(required = false) String authorization,
                                                            @RequestParam String kind, @RequestPart MultipartFile file) {
        return ApiResponse.success(transcriptions.forPhoto(photoId, authorization, kind, file));
    }

    @PostMapping(value = "/askbacks/{askbackId}/transcriptions", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TranscriptionService.DraftView> askback(@PathVariable String askbackId, @RequestHeader(required = false) String authorization,
                                                              @RequestPart MultipartFile file) {
        return ApiResponse.success(transcriptions.forAskback(askbackId, authorization, file));
    }

    @PostMapping(value = "/paragraphs/{paragraphId}/transcriptions", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TranscriptionService.DraftView> paragraph(@PathVariable String paragraphId, @RequestHeader(required = false) String authorization,
                                                                @RequestPart MultipartFile file) {
        return ApiResponse.success(transcriptions.forParagraph(paragraphId, authorization, file));
    }
}
