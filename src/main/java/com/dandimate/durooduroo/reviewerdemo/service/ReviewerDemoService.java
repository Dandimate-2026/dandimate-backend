package com.dandimate.durooduroo.reviewerdemo.service;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.FlowerCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.EntryKind;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.reviewerdemo.domain.ReviewerDemoSession;
import com.dandimate.durooduroo.reviewerdemo.dto.ReviewerDemoView;
import com.dandimate.durooduroo.reviewerdemo.repository.ReviewerDemoSessionRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomPlan;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.scroll.domain.Paragraph;
import com.dandimate.durooduroo.scroll.domain.ParagraphSource;
import com.dandimate.durooduroo.scroll.domain.Scroll;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphSourceRepository;
import com.dandimate.durooduroo.scroll.repository.ScrollRepository;
import com.dandimate.durooduroo.storage.ObjectStorage;
import java.time.LocalDate;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 심사자가 자신의 카카오 계정으로 안전하게 열어 볼 수 있는 예시 데이터를 만든다.
 * 다른 사용자의 실제 데이터는 복사하지 않고, 로그인 사용자별로 별도 방 세 개를 만든다.
 */
@Service
public class ReviewerDemoService {
    private static final byte[] DEMO_IMAGE = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6HvoAAAAASUVORK5CYII=");

    private final ReviewerDemoSessionRepository sessions;
    private final RoomRepository rooms;
    private final MemberRepository members;
    private final PhotoRepository photos;
    private final MemoryEntryRepository entries;
    private final ScrollRepository scrolls;
    private final ParagraphRepository paragraphs;
    private final ParagraphSourceRepository sources;
    private final ObjectStorage storage;
    private final boolean enabled;

    public ReviewerDemoService(ReviewerDemoSessionRepository sessions, RoomRepository rooms, MemberRepository members,
                               PhotoRepository photos, MemoryEntryRepository entries, ScrollRepository scrolls,
                               ParagraphRepository paragraphs, ParagraphSourceRepository sources, ObjectStorage storage,
                               @Value("${app.reviewer-demo.enabled:false}") boolean enabled) {
        this.sessions = sessions; this.rooms = rooms; this.members = members; this.photos = photos; this.entries = entries;
        this.scrolls = scrolls; this.paragraphs = paragraphs; this.sources = sources; this.storage = storage; this.enabled = enabled;
    }

    @Transactional
    public ReviewerDemoView activate(String userId) {
        if (!enabled) throw new BusinessException(ErrorCode.FORBIDDEN, "심사용 데모는 아직 열려 있지 않아요.");
        var existing = sessions.findByUserId(userId);
        if (existing.isPresent() && allRoomsExist(existing.get())) return view(existing.get());
        // 데모 방을 관리자가 지운 경우에는 같은 사용자에게 새 묶음을 다시 만든다.
        existing.ifPresent(sessions::delete);
        return create(userId);
    }

    private boolean allRoomsExist(ReviewerDemoSession session) {
        return rooms.existsById(session.getHostRoomId()) && rooms.existsById(session.getMemberRoomId())
                && rooms.existsById(session.getCompletedRoomId());
    }

    private ReviewerDemoView create(String userId) {
        Room hostRoom = newRoom("[심사용] 경주 가을 산책", LocalDate.of(2026, 11, 7));
        Member host = members.save(Member.host(hostRoom.getId(), "운영진", userId));
        Member seoyeon = members.save(Member.join(hostRoom.getId(), "서연", FlowerCode.FORSYTHIA));
        seedPhoto(hostRoom, host, "황리단길", "경주 황리단길", "경주", 35.8428, 129.2119,
                "골목을 걷다가 마음에 드는 작은 가게 앞에서 한참 사진을 찍었어요.");
        seedPhoto(hostRoom, seoyeon, "대릉원", "경주 대릉원", "경주", 35.8380, 129.2127,
                "해가 질 때쯤 잔디 위에 길게 늘어진 그림자가 참 예뻤어요.");
        hostRoom.startCirculating();

        Room memberRoom = newRoom("[심사용] 포항 바다의 밤", LocalDate.of(2026, 10, 25));
        Member minji = members.save(Member.host(memberRoom.getId(), "민지"));
        Member reviewer = members.save(Member.join(memberRoom.getId(), "운영진", FlowerCode.FORGET_ME_NOT, userId));
        Member junho = members.save(Member.join(memberRoom.getId(), "준호", FlowerCode.LILAC));
        seedPhoto(memberRoom, minji, "영일대 해수욕장", "포항 영일대 해수욕장", "포항", 36.0541, 129.3788,
                "바닷바람이 세서 웃음이 계속 났고, 멀리 보이는 불빛이 반짝였어요.");
        seedPhoto(memberRoom, junho, "스페이스워크", "포항 스페이스워크", "포항", 36.0672, 129.3845,
                "위에 올라가니 바다와 도시가 한눈에 보여서 모두 한참 말을 잃었어요.");
        memberRoom.startCirculating();

        Room completedRoom = newRoom("[심사용] 안동 하루 여행", LocalDate.of(2026, 10, 12));
        completedRoom.changeScrollTemplate("ANDONG");
        Member jiwon = members.save(Member.host(completedRoom.getId(), "지원"));
        Member completedReviewer = members.save(Member.join(completedRoom.getId(), "운영진", FlowerCode.CANOLA, userId));
        Photo completedPhoto = seedPhoto(completedRoom, completedReviewer, "하회마을", "안동 하회마을", "안동", 36.5391, 128.5175,
                "기와지붕 사이로 들어오는 햇빛이 포근해서, 오늘의 속도를 조금 늦추고 싶었어요.");
        MemoryEntry support = entries.save(MemoryEntry.confirm(completedRoom.getId(), completedPhoto.getId(), jiwon.getId(),
                EntryKind.ADDON, "TEXT", null, null, "골목 끝에서 다 같이 멈춰 서서 사진을 찍던 순간이 가장 기억나요."));
        completedRoom.startCirculating();
        completedRoom.startWeaving();
        completedRoom.completeWeaving();
        Scroll scroll = scrolls.save(Scroll.create(completedRoom.getId(), 1,
                "안동의 오후는 천천히 걸을수록 더 오래 남았습니다."));
        Paragraph paragraph = paragraphs.save(Paragraph.create(completedRoom.getId(), scroll.getId(), completedPhoto.getId(), 1,
                "운영진은 ‘기와지붕 사이로 들어오는 햇빛이 포근해서’ 오늘의 속도를 조금 늦추고 싶었다고 떠올렸어요. 지원은 골목 끝에서 함께 멈춰 선 순간을 기억했어요."));
        sources.save(ParagraphSource.of(paragraph.getId(), support.getId(), jiwon.getId(), support.getTranscript(), 1));

        rooms.save(hostRoom); rooms.save(memberRoom); rooms.save(completedRoom);
        ReviewerDemoSession session = sessions.save(ReviewerDemoSession.create(userId, hostRoom.getId(), memberRoom.getId(), completedRoom.getId()));
        return view(session);
    }

    private Room newRoom(String title, LocalDate meetingDate) {
        Room room = Room.create(title, meetingDate, 4, RoomPlan.LITE);
        room.changePlace(new com.dandimate.durooduroo.room.dto.RoomPlace(title, null, 35.84, 129.21));
        return rooms.save(room);
    }

    private Photo seedPhoto(Room room, Member author, String place, String address, String city, double latitude, double longitude, String text) {
        String key = "reviewer-demo/" + room.getId() + "/" + author.getId() + ".png";
        storage.putBytes(key, DEMO_IMAGE, "image/png");
        Photo photo = Photo.pending(java.util.UUID.randomUUID().toString(), room.getId(), author.getId(), key, storage.locationFor(key), "image/png", 0);
        photo.complete(java.util.UUID.randomUUID().toString().replace("-", ""));
        photo.locate(place, address, city, latitude, longitude);
        photo.publish();
        Photo saved = photos.save(photo);
        entries.save(MemoryEntry.confirm(room.getId(), saved.getId(), author.getId(), EntryKind.FIRST, "TEXT", null, null, text));
        return saved;
    }

    private ReviewerDemoView view(ReviewerDemoSession session) {
        return new ReviewerDemoView(session.getHostRoomId(), session.getMemberRoomId(), session.getCompletedRoomId());
    }
}
