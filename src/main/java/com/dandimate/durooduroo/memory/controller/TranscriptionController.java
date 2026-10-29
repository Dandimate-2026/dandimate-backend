/** 명세서 기준 음성 받아쓰기 API입니다. 실제 STT 교체 전까지 업로드 검증과 초안 ID를 제공합니다. */
package com.dandimate.durooduroo.memory.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.ai.service.OpenAiTranscriptionService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/photos/{photoId}/transcriptions")
public class TranscriptionController {
    private final PhotoRepository photos; private final MemberRepository members; private final OpenAiTranscriptionService stt;
    public TranscriptionController(PhotoRepository photos, MemberRepository members, OpenAiTranscriptionService stt) { this.photos=photos; this.members=members; this.stt=stt; }

    @PostMapping(consumes="multipart/form-data")
    public ApiResponse<?> transcribe(@PathVariable String photoId, @RequestHeader(required=false) String authorization,
                                     @RequestParam(required=false) String memberToken, @RequestParam String kind,
                                     @RequestPart MultipartFile file) {
        var photo=photos.findById(photoId).orElseThrow(()->new BusinessException("PHOTO_NOT_FOUND","사진이 없습니다."));
        String token=MemberTokenResolver.resolve(authorization,memberToken);
        members.findByRoomIdAndMemberToken(photo.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 받아쓰기를 사용할 수 있습니다."));
        if(file.isEmpty() || file.getSize()>10_000_000) throw new BusinessException("INVALID_AUDIO","음성 파일은 10MB 이하이어야 합니다.");
        String transcript=stt.transcribe(file);
        return ApiResponse.success(new TranscriptionDraft(java.util.UUID.randomUUID().toString(), java.util.UUID.randomUUID().toString(), transcript, List.of("ko"), LocalDateTime.now().plusHours(24)));
    }
    public record TranscriptionDraft(String transcriptionId,String audioAssetId,String transcript,List<String> detectedLanguages,LocalDateTime expiresAt) {}
}
