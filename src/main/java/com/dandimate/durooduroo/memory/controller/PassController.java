/** 명세서 기준 사진 패스 등록·취소 API입니다. */
package com.dandimate.durooduroo.memory.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.*;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/photos/{photoId}/passes/me")
public class PassController {
    private final PhotoRepository photos; private final MemberRepository members; private final MemoryEntryRepository entries; private final com.dandimate.durooduroo.room.service.RoomStateService roomStates;
    public PassController(PhotoRepository photos,MemberRepository members,MemoryEntryRepository entries,com.dandimate.durooduroo.room.service.RoomStateService roomStates){this.photos=photos;this.members=members;this.entries=entries;this.roomStates=roomStates;}
    @PutMapping
    public ApiResponse<?> pass(@PathVariable String photoId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){var photo=photos.findById(photoId).orElseThrow(()->new BusinessException("PHOTO_NOT_FOUND","사진이 없습니다.")); String token=MemberTokenResolver.resolve(authorization,memberToken); var member=members.findByRoomIdAndMemberToken(photo.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 패스할 수 있습니다.")); roomStates.require(photo.getRoomId(),com.dandimate.durooduroo.room.domain.RoomAction.ADDON_OR_PASS); entries.findByPhotoIdAndMemberId(photoId,member.getId()).ifPresent(entries::delete); return ApiResponse.success(entries.save(MemoryEntry.create(photo.getRoomId(),photoId,member.getId(),null,EntryStatus.SKIPPED)));}
    @DeleteMapping
    public ApiResponse<?> cancel(@PathVariable String photoId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){var photo=photos.findById(photoId).orElseThrow(()->new BusinessException("PHOTO_NOT_FOUND","사진이 없습니다.")); String token=MemberTokenResolver.resolve(authorization,memberToken); var member=members.findByRoomIdAndMemberToken(photo.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 사용할 수 있습니다.")); roomStates.require(photo.getRoomId(),com.dandimate.durooduroo.room.domain.RoomAction.ADDON_OR_PASS); entries.findByPhotoIdAndMemberId(photoId,member.getId()).ifPresent(entries::delete); return ApiResponse.success(java.util.Map.of("status","NONE"));}
}
