/** 명세서 기준 사진 업로드 요청·완료 API입니다. 로컬 파일 저장소를 사용하는 MVP 구현입니다. */
package com.dandimate.durooduroo.photo.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.dto.UploadRequest;
import com.dandimate.durooduroo.photo.dto.UploadResponse;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/photos")
public class PhotoSpecController {
    private final RoomRepository rooms; private final MemberRepository members; private final PhotoRepository photos;
    public PhotoSpecController(RoomRepository rooms, MemberRepository members, PhotoRepository photos) { this.rooms=rooms; this.members=members; this.photos=photos; }

    @PostMapping("/upload-requests")
    public ApiResponse<UploadResponse> request(@PathVariable String roomId, @RequestHeader(required=false) String authorization,
                                                @RequestParam(required=false) String memberToken, @Valid @RequestBody UploadRequest request) {
        String token = MemberTokenResolver.resolve(authorization, memberToken);
        var member = members.findByRoomIdAndMemberToken(roomId, token).orElseThrow(() -> new BusinessException("MEMBER_FORBIDDEN", "방 참여자만 업로드할 수 있습니다."));
        if (!rooms.existsById(roomId)) throw new BusinessException("ROOM_NOT_FOUND", "존재하지 않는 방입니다.");
        if (photos.countByRoomIdAndMemberId(roomId, member.getId()) + request.files().size() > 3) throw new BusinessException("LIMIT_REACHED", "사진은 최대 3장까지 업로드할 수 있습니다.");
        var uploads = request.files().stream().map(file -> {
            String photoId = java.util.UUID.randomUUID().toString();
            photos.save(Photo.createWithId(photoId, roomId, member.getId(), "pending://" + photoId, "pending-" + file.clientFileId()));
            return new UploadResponse.Upload(file.clientFileId(), photoId, "/api/v1/photos/" + photoId + "/complete", LocalDateTime.now().plusMinutes(10), java.util.Map.of("Content-Type", file.contentType()));
        }).toList();
        return ApiResponse.success(new UploadResponse(uploads));
    }
}
