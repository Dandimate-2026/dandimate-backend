package com.dandimate.durooduroo.room.dto;
/** imageUrl is an authenticated, same-origin URL; clients must resolve it against the API origin. */
public record CoverPhotoView(String id, String imageUrl) {
    public static CoverPhotoView from(String id) {
        return id == null ? null : new CoverPhotoView(id, "/api/v1/room-covers/" + id + "/image");
    }
}
