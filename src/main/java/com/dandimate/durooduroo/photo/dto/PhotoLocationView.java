/** 사진에 입력한 장소입니다. 입력하지 않았으면 null입니다. */
package com.dandimate.durooduroo.photo.dto;

import com.dandimate.durooduroo.photo.domain.Photo;

public record PhotoLocationView(String placeName, String address, String city, Double latitude, Double longitude) {

    public static PhotoLocationView of(Photo photo) {
        if (photo.getPlaceName() == null) return null;
        return new PhotoLocationView(photo.getPlaceName(), photo.getAddress(), photo.getCity(), photo.getLatitude(), photo.getLongitude());
    }
}
