/** 사진 업로드 결과를 반환하는 DTO입니다. */
package com.dandimate.durooduroo.photo.dto;

import com.dandimate.durooduroo.photo.domain.Photo;

public record PhotoResponse(String photoId, String roomId, String memberId, String fileUrl, boolean duplicate) {
    public static PhotoResponse from(Photo p, boolean duplicate) { return new PhotoResponse(p.getId(), p.getRoomId(), p.getMemberId(), p.getFileUrl(), duplicate); }
}
