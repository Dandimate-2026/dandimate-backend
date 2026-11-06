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
    private final com.dandimate.durooduroo.storage.MediaUrls mediaUrls;

    public PhotoMapService(MemberRepository members, RoomRepository rooms, PhotoRepository photos, MemoryEntryRepository entries, com.dandimate.durooduroo.storage.MediaUrls mediaUrls) {
        this.mediaUrls = mediaUrls;
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
        var photoEntries = entries.findAllByPhotoIdIn(located.stream().map(Photo::getId).toList());
        var firstByPhoto = photoEntries.stream().filter(entry -> entry.getKind() == com.dandimate.durooduroo.memory.domain.EntryKind.FIRST)
                .collect(Collectors.toMap(MemoryEntry::getPhotoId, MemoryEntry::getTranscript, (a,b)->a));
        var uploaders = members.findAllById(located.stream().map(Photo::getMemberId).distinct().toList()).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<String, Long> recordsByPhoto = photoEntries.stream()
                .collect(Collectors.groupingBy(MemoryEntry::getPhotoId, Collectors.counting()));

        List<PhotoMapView.CityPin> cities = located.stream().collect(Collectors.groupingBy(Photo::getCity)).entrySet().stream()
                .map(city -> cityPin(city.getKey(), city.getValue(), roomById, recordsByPhoto, firstByPhoto, uploaders))
                .sorted(Comparator.comparing((PhotoMapView.CityPin pin) -> latestMeeting(pin)).reversed().thenComparing(PhotoMapView.CityPin::city))
                .toList();
        return new PhotoMapView(cities);
    }

    private PhotoMapView.CityPin cityPin(String city, List<Photo> cityPhotos, Map<String, Room> roomById, Map<String, Long> recordsByPhoto, Map<String,String> firstByPhoto, Map<String,Member> uploaders) {
        List<PhotoMapView.RoomItem> roomItems = cityPhotos.stream().collect(Collectors.groupingBy(Photo::getRoomId)).entrySet().stream()
                .map(room -> roomItem(roomById.get(room.getKey()), room.getValue(), recordsByPhoto, firstByPhoto))
                .sorted(Comparator.comparing(PhotoMapView.RoomItem::meetingDate).reversed())
                .toList();
        List<PhotoMapView.PlaceItem> places = cityPhotos.stream()
                .map(photo -> new PhotoMapView.PlaceItem(photo.getId(), photo.getRoomId(), photo.getPlaceName(), photo.getLatitude(), photo.getLongitude(),
                    mediaUrls.imageUrl(photo), uploader(uploaders.get(photo.getMemberId())), snippet(firstByPhoto.get(photo.getId())), photo.getPlaceId()))
                .toList();
        return new PhotoMapView.CityPin(city,
                cityPhotos.stream().mapToDouble(Photo::getLatitude).average().orElseThrow(),
                cityPhotos.stream().mapToDouble(Photo::getLongitude).average().orElseThrow(),
                cityPhotos.size(), records(cityPhotos, recordsByPhoto),
                roomItems.stream().anyMatch(PhotoMapView.RoomItem::scrollReady),
                roomItems.stream().map(PhotoMapView.RoomItem::summary).filter(Objects::nonNull).findFirst().orElse(null), roomItems, places);
    }

    private PhotoMapView.RoomItem roomItem(Room room, List<Photo> roomPhotos, Map<String, Long> recordsByPhoto, Map<String,String> firstByPhoto) {
        String summary=room.getScrollSummary();
        if(summary==null) {
            String places=roomPhotos.stream().map(Photo::getPlaceName).filter(Objects::nonNull).distinct().sorted().collect(Collectors.joining(", "));
            String text=roomPhotos.stream().sorted(Comparator.comparing(Photo::getCreatedAt).thenComparing(Photo::getId))
              .map(p->firstByPhoto.get(p.getId())).filter(Objects::nonNull).collect(Collectors.joining(" · "));
            summary=places+(text.isBlank()?"에서 사진 "+roomPhotos.size()+"장의 추억을 모으고 있어요.":" · "+text);
            if(summary.codePointCount(0,summary.length())>200)summary=summary.substring(0,summary.offsetByCodePoints(0,199))+"…";
        }
        return new PhotoMapView.RoomItem(room.getId(), room.getTitle(), room.getMeetingDate(), room.getStatus().name(),
                room.getStatus() == RoomStatus.GROWING, roomPhotos.size(), records(roomPhotos, recordsByPhoto), summary);
    }

    private int records(List<Photo> photos, Map<String, Long> recordsByPhoto) {
        return photos.stream().mapToInt(photo -> recordsByPhoto.getOrDefault(photo.getId(), 0L).intValue()).sum();
    }

    private PhotoMapView.Uploader uploader(Member member) {
        return member == null ? null : new PhotoMapView.Uploader(member.getUserId(), member.getNickname(), member.getFlowerCode().name());
    }
    private String snippet(String text) {
        return text == null ? null : text.substring(0, text.offsetByCodePoints(0, Math.min(100, text.codePointCount(0,text.length()))));
    }

    private LocalDate latestMeeting(PhotoMapView.CityPin pin) {
        return pin.rooms().get(0).meetingDate();
    }
}
