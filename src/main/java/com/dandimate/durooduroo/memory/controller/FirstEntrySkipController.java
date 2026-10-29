/** 명세서 기준 첫 한마디 나중에 처리 API입니다. */
package com.dandimate.durooduroo.memory.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.EntryStatus;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/photos/{photoId}/first-entry")
public class FirstEntrySkipController {
    private final PhotoRepository photos; private final MemberRepository members; private final MemoryEntryRepository entries; private final com.dandimate.durooduroo.room.service.RoomStateService roomStates;
    public FirstEntrySkipController(PhotoRepository photos, MemberRepository members, MemoryEntryRepository entries, com.dandimate.durooduroo.room.service.RoomStateService roomStates) { this.photos=photos;this.members=members;this.entries=entries;this.roomStates=roomStates; }
    @PostMapping("/skip")
    public ApiResponse<?> skip(@PathVariable String photoId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){
        var photo=photos.findById(photoId).orElseThrow(()->new BusinessException("PHOTO_NOT_FOUND","사진이 없습니다.")); String token=MemberTokenResolver.resolve(authorization,memberToken);
        var member=members.findByRoomIdAndMemberToken(photo.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 패스할 수 있습니다."));
        if(!member.getId().equals(photo.getMemberId())) throw new BusinessException("FORBIDDEN","사진을 올린 사람만 첫 한마디를 미룰 수 있습니다.");
        roomStates.require(photo.getRoomId(),com.dandimate.durooduroo.room.domain.RoomAction.UPLOAD_OR_FIRST_ENTRY);
        if(entries.existsByPhotoIdAndMemberId(photoId,member.getId())) throw new BusinessException("DUPLICATE_ENTRY","이미 제출했습니다.");
        return ApiResponse.success(entries.save(MemoryEntry.create(photo.getRoomId(),photoId,member.getId(),null,EntryStatus.SKIPPED)));
    }
}
