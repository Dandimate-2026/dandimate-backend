/** OpenAPI Photo 스키마입니다. */
package com.dandimate.durooduroo.photo.dto;

import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.dto.MemberView;
import com.dandimate.durooduroo.photo.domain.Photo;

public record PhotoView(String id, String imageUrl, MemberView uploader, int displayOrder) {

    public static PhotoView of(Photo photo, Member uploader) {
        return new PhotoView(photo.getId(), photo.getFileUrl(), MemberView.from(uploader), photo.getDisplayOrder());
    }
}
