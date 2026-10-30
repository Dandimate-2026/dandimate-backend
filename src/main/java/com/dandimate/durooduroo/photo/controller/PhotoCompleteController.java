/** 명세서 기준 사진 업로드 완료 API입니다. */
package com.dandimate.durooduroo.photo.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.photo.dto.PhotoResponse;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.storage.ObjectStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/photos")
public class PhotoCompleteController {
    private final PhotoRepository photos; private final MemberRepository members; private final ObjectStorage storage; private final com.dandimate.durooduroo.room.service.RoomStateService roomStates;
    public PhotoCompleteController(PhotoRepository photos, MemberRepository members, ObjectStorage storage, com.dandimate.durooduroo.room.service.RoomStateService roomStates) { this.photos=photos; this.members=members; this.storage=storage; this.roomStates=roomStates; }

    @PostMapping(value="/{photoId}/complete", consumes="multipart/form-data")
    public ApiResponse<PhotoResponse> complete(@PathVariable String photoId, @RequestHeader(required=false) String authorization, @RequestPart(required=false) MultipartFile file) throws Exception {
        var photo = photos.findById(photoId).orElseThrow(() -> new BusinessException("PHOTO_NOT_FOUND", "사진이 없습니다."));
        String token = MemberTokenResolver.resolve(authorization);
        var member = members.findByRoomIdAndMemberToken(photo.getRoomId(), token).orElseThrow(() -> new BusinessException("MEMBER_FORBIDDEN", "본인 사진만 완료할 수 있습니다."));
        if (!member.getId().equals(photo.getMemberId())) throw new BusinessException("FORBIDDEN", "본인 사진만 완료할 수 있습니다.");
        roomStates.require(photo.getRoomId(), com.dandimate.durooduroo.room.domain.RoomAction.UPLOAD_OR_FIRST_ENTRY);
        if (file != null && !file.isEmpty()) {
            if (file.getContentType() == null || !file.getContentType().startsWith("image/")) throw new BusinessException("UPLOAD_VERIFICATION_FAILED", "이미지 파일만 업로드할 수 있습니다.");
            if (file.getSize() > 2_000_000) throw new BusinessException("FILE_TOO_LARGE", "사진은 2MB 이하이어야 합니다.");
            String key = photo.getRoomId()+"/"+UUID.randomUUID()+extension(file.getOriginalFilename()); String storedUrl=storage.put(key,file);
            photo.complete(storedUrl, sha256(file.getBytes())); photos.save(photo);
        }
        return ApiResponse.success(PhotoResponse.from(photo, false));
    }

    private String extension(String name) { return name!=null && name.matches(".*\\.[A-Za-z0-9]{1,5}") ? name.substring(name.lastIndexOf('.')).toLowerCase() : ""; }

    private String sha256(byte[] bytes) throws Exception { byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder value=new StringBuilder(); for(byte b:digest)value.append(String.format("%02x",b)); return value.toString(); }
}
