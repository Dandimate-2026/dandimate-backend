/** 사진 장소를 입력·삭제합니다. 사진을 올린 본인만 바꿀 수 있습니다. */
package com.dandimate.durooduroo.photo.service;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.domain.PlaceCity;
import com.dandimate.durooduroo.photo.dto.PhotoLocationRequest;
import com.dandimate.durooduroo.photo.dto.PhotoLocationView;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.RoomAction;
import com.dandimate.durooduroo.room.service.RoomStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoLocationService {
    @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.scroll.service.ScrollSummaryService summaries;
    private final PhotoRepository photos;
    private final MemberRepository members;
    private final RoomStateService roomStates;

    public PhotoLocationService(PhotoRepository photos, MemberRepository members, RoomStateService roomStates) {
        this.photos = photos;
        this.members = members;
        this.roomStates = roomStates;
    }

    @Transactional
    public PhotoLocationView set(String photoId, String authorization, PhotoLocationRequest request) {
        Photo photo = editable(photoId, authorization);
        if (!photo.isReady() && !Photo.PROCESSING.equals(photo.getStatus()))
            throw new BusinessException(ErrorCode.PHOTO_NOT_READY, "업로드 완료 후 장소를 저장해 주세요.");
        if ((request.latitude() == null) != (request.longitude() == null))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "위도와 경도는 함께 보내 주세요.");
        String address = request.address() == null || request.address().isBlank() ? null : request.address().strip();
        if (request.latitude() != null && address == null)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "좌표를 보내려면 주소도 함께 보내 주세요.");
        photo.locate(request.placeName().strip(), address, PlaceCity.of(address), request.latitude(), request.longitude());
        photo.identifyPlace(request.placeId() == null || request.placeId().isBlank() ? null : request.placeId().strip());
        if(photo.isPublished())summaries.enqueue(photo.getRoomId());
        return PhotoLocationView.of(photo);
    }

    @Transactional
    public void clear(String photoId, String authorization) {
        var photo=editable(photoId, authorization);photo.clearLocation();
        if(photo.isPublished())summaries.enqueue(photo.getRoomId());
    }

    private Photo editable(String photoId, String authorization) {
        Photo photo = photos.findById(photoId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다."));
        String token = MemberTokenResolver.resolve(authorization);
        var member = members.findByRoomIdAndMemberToken(photo.getRoomId(), token)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "참여자만 장소를 바꿀 수 있습니다."));
        if (!member.getId().equals(photo.getMemberId())) throw new BusinessException(ErrorCode.FORBIDDEN, "본인 사진의 장소만 바꿀 수 있습니다.");
        roomStates.require(photo.getRoomId(), RoomAction.EDIT_PHOTO_LOCATION);
        return photo;
    }
}
