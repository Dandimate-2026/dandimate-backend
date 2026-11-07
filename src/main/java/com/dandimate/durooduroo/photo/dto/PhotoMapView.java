/** 내 지도: 내가 참여 중인 방의 사진 장소를 도시별로 묶은 응답입니다. */
package com.dandimate.durooduroo.photo.dto;

import java.time.LocalDate;
import java.util.List;

public record PhotoMapView(List<CityPin> cities) {

    /** latitude·longitude는 그 도시 사진 좌표의 평균이다. bloomed는 완성된 두루마리가 하나라도 있는지. summary는 그 도시의 가장 최근 요약이다. */
    public record CityPin(String city, String adminAreaCode, double latitude, double longitude, int photoCount, int recordCount, boolean bloomed, String summary, String summaryStatus,
                          List<RoomItem> rooms, List<PlaceItem> places) {
    }

    public record RoomItem(String roomId, String name, LocalDate meetingDate, String status, boolean scrollReady,
                           int photoCount, int recordCount, String summary, String summaryStatus) {
    }

    public record Uploader(String userId, String nickname, String flowerCode) {}

    public record PlaceItem(String photoId, String roomId, String placeName, String city, String adminAreaCode,
                            double latitude, double longitude, String imageUrl, Uploader uploader, String firstEntrySnippet,
                            String placeId, String placeGroupKey, int recordCount, List<String> recordIds) {
    }
}
