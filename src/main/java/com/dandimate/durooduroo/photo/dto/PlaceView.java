/** 장소 검색 결과 한 건입니다. 고른 값을 그대로 사진 장소 입력에 보냅니다. */
package com.dandimate.durooduroo.photo.dto;

public record PlaceView(String placeName, String address, String city, double latitude, double longitude, String placeId) {
    public PlaceView(String name,String address,String city,double lat,double lng) { this(name,address,city,lat,lng,null); }
}
