/**
 * 내 지도를 만듭니다. 내가 지금 참여 중인 방에서 돌려보기에 공개된, 좌표가 있는 사진만 씁니다.
 * 기록 수는 그 사진들의 첫 한마디·덧붙임·되묻기 답을 합한 값입니다.
 */
package com.dandimate.durooduroo.photo.service;

import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.dto.PhotoMapView;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoMapService {
    private final MemberRepository members;
    private final RoomRepository rooms;
    private final PhotoRepository photos;
    private final MemoryEntryRepository entries;

    public PhotoMapService(MemberRepository members, RoomRepository rooms, PhotoRepository photos, MemoryEntryRepository entries) {
        this.members = members;
        this.rooms = rooms;
        this.photos = photos;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public PhotoMapView map(String userId) {
        List<String> roomIds = members.findAllByUserIdAndLeftAtIsNull(userId).stream().map(Member::getRoomId).toList();
        Map<String, Room> roomById = rooms.findAllById(roomIds).stream().collect(Collectors.toMap(Room::getId, Function.identity()));
        List<Photo> located = photos.findAllByRoomIdInAndPublishedAtIsNotNullAndLatitudeIsNotNull(roomById.keySet()).stream()
                .filter(photo -> photo.isReady() && photo.getCity() != null).toList();
        Map<String, Long> recordsByPhoto = entries.findAllByPhotoIdIn(located.stream().map(Photo::getId).toList()).stream()
                .collect(Collectors.groupingBy(MemoryEntry::getPhotoId, Collectors.counting()));

        List<PhotoMapView.CityPin> cities = located.stream().collect(Collectors.groupingBy(Photo::getCity)).entrySet().stream()
                .map(city -> cityPin(city.getKey(), city.getValue(), roomById, recordsByPhoto))
                .sorted(Comparator.comparing((PhotoMapView.CityPin pin) -> latestMeeting(pin)).reversed().thenComparing(PhotoMapView.CityPin::city))
                .toList();
        return new PhotoMapView(cities);
    }

    private PhotoMapView.CityPin cityPin(String city, List<Photo> cityPhotos, Map<String, Room> roomById, Map<String, Long> recordsByPhoto) {
        List<PhotoMapView.RoomItem> roomItems = cityPhotos.stream().collect(Collectors.groupingBy(Photo::getRoomId)).entrySet().stream()
                .map(room -> roomItem(roomById.get(room.getKey()), room.getValue(), recordsByPhoto))
                .sorted(Comparator.comparing(PhotoMapView.RoomItem::meetingDate).reversed())
                .toList();
        List<PhotoMapView.PlaceItem> places = cityPhotos.stream()
                .map(photo -> new PhotoMapView.PlaceItem(photo.getId(), photo.getRoomId(), photo.getPlaceName(), photo.getLatitude(), photo.getLongitude()))
                .toList();
        return new PhotoMapView.CityPin(city,
                cityPhotos.stream().mapToDouble(Photo::getLatitude).average().orElseThrow(),
                cityPhotos.stream().mapToDouble(Photo::getLongitude).average().orElseThrow(),
                cityPhotos.size(), records(cityPhotos, recordsByPhoto),
                roomItems.stream().anyMatch(PhotoMapView.RoomItem::scrollReady),
                roomItems.stream().map(PhotoMapView.RoomItem::summary).filter(Objects::nonNull).findFirst().orElse(null), roomItems, places);
    }

    private PhotoMapView.RoomItem roomItem(Room room, List<Photo> roomPhotos, Map<String, Long> recordsByPhoto) {
        return new PhotoMapView.RoomItem(room.getId(), room.getTitle(), room.getMeetingDate(), room.getStatus().name(),
                room.getStatus() == RoomStatus.GROWING, roomPhotos.size(), records(roomPhotos, recordsByPhoto), room.getScrollSummary());
    }

    private int records(List<Photo> photos, Map<String, Long> recordsByPhoto) {
        return photos.stream().mapToInt(photo -> recordsByPhoto.getOrDefault(photo.getId(), 0L).intValue()).sum();
    }

    private LocalDate latestMeeting(PhotoMapView.CityPin pin) {
        return pin.rooms().get(0).meetingDate();
    }
}
