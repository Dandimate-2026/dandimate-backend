/** 명세서 기준 본인 사진 삭제 API입니다. */
package com.dandimate.durooduroo.photo.controller;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.service.EntryService;
import com.dandimate.durooduroo.notification.service.RoomEvents;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.RoomAction;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.service.RoomStateService;
import com.dandimate.durooduroo.scroll.service.ScrollSummaryService;
import com.dandimate.durooduroo.storage.ObjectStorage;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/photos")
public class PhotoDeleteController {

    private final PhotoRepository photos;
    private final MemberRepository members;
    private final RoomStateService roomStates;
    private final EntryService entryService;
    private final ObjectStorage storage;
    private final ScrollSummaryService summaries;
    private final RoomEvents events;

    public PhotoDeleteController(PhotoRepository photos, MemberRepository members, RoomStateService roomStates,
                                 EntryService entryService, ObjectStorage storage, ScrollSummaryService summaries,
                                 RoomEvents events) {
        this.photos = photos;
        this.members = members;
        this.roomStates = roomStates;
        this.entryService = entryService;
        this.storage = storage;
        this.summaries = summaries;
        this.events = events;
    }

    /** 본인 사진만 지운다. 이미 두루마리가 엮인 방이면 한 줄 요약을 다시 만든다. */
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> delete(@PathVariable String photoId,
                                       @RequestHeader(required = false) String authorization) {
        var photo = photos.findById(photoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다."));
        var member = members.findByRoomIdAndMemberToken(photo.getRoomId(), MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "참여자만 삭제할 수 있습니다."));
        if (!member.getId().equals(photo.getMemberId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 사진만 삭제할 수 있습니다.");
        }
        var room = roomStates.require(photo.getRoomId(), RoomAction.DELETE_CONTENT);
        entryService.deleteForPhoto(photoId);
        photos.delete(photo);
        storage.delete(photo.getFileUrl());
        if (room.getStatus() == RoomStatus.GROWING) {
            summaries.enqueue(photo.getRoomId());
        }
        events.toRoom(photo.getRoomId(), "photo.deleted", Map.of("photoId", photoId));
        return ResponseEntity.noContent().build();
    }
}
