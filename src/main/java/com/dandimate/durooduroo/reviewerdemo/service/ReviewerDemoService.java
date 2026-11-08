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
import com.dandimate.durooduroo.room.service.RoomAdminService;
import com.dandimate.durooduroo.scroll.domain.Paragraph;
import com.dandimate.durooduroo.scroll.domain.ParagraphSource;
import com.dandimate.durooduroo.scroll.domain.Scroll;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphSourceRepository;
import com.dandimate.durooduroo.scroll.repository.ScrollRepository;
import com.dandimate.durooduroo.storage.ObjectStorage;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 심사자가 자신의 카카오 계정으로 안전하게 열어 볼 수 있는 예시 데이터를 만든다.
 * 다른 사용자의 실제 데이터는 복사하지 않고, 로그인 사용자별로 별도 방 세 개를 만든다.
 */
@Service
public class ReviewerDemoService {
    private static final int CURRENT_SEED_VERSION = 2;

    private final ReviewerDemoSessionRepository sessions;
    private final RoomRepository rooms;
    private final MemberRepository members;
    private final PhotoRepository photos;
    private final MemoryEntryRepository entries;
    private final ScrollRepository scrolls;
    private final ParagraphRepository paragraphs;
    private final ParagraphSourceRepository sources;
    private final ObjectStorage storage;
    private final RoomAdminService roomAdmin;
    private final boolean enabled;

    public ReviewerDemoService(ReviewerDemoSessionRepository sessions, RoomRepository rooms, MemberRepository members,
                               PhotoRepository photos, MemoryEntryRepository entries, ScrollRepository scrolls,
                               ParagraphRepository paragraphs, ParagraphSourceRepository sources, ObjectStorage storage, RoomAdminService roomAdmin,
                               @Value("${app.reviewer-demo.enabled:false}") boolean enabled) {
        this.sessions = sessions; this.rooms = rooms; this.members = members; this.photos = photos; this.entries = entries;
        this.scrolls = scrolls; this.paragraphs = paragraphs; this.sources = sources; this.storage = storage; this.roomAdmin = roomAdmin; this.enabled = enabled;
    }

    @Transactional
    public ReviewerDemoView activate(String userId) {
        if (!enabled) throw new BusinessException(ErrorCode.FORBIDDEN, "심사용 데모는 아직 열려 있지 않아요.");
        var existing = sessions.findByUserId(userId);
        if (existing.isPresent() && allRoomsExist(existing.get())
                && existing.get().getSeedVersion() == CURRENT_SEED_VERSION) return view(existing.get());
        // 사진/예시 내용이 바뀌었거나 데모 방을 관리자가 지운 경우 새 묶음으로 교체한다.
        existing.ifPresent(this::removeExistingDemo);
        return create(userId);
    }

    private void removeExistingDemo(ReviewerDemoSession session) {
        sessions.delete(session);
        sessions.flush();
        roomAdmin.deleteReviewerDemoRoom(session.getHostRoomId());
        roomAdmin.deleteReviewerDemoRoom(session.getMemberRoomId());
        roomAdmin.deleteReviewerDemoRoom(session.getCompletedRoomId());
    }

    private boolean allRoomsExist(ReviewerDemoSession session) {
        return rooms.existsById(session.getHostRoomId()) && rooms.existsById(session.getMemberRoomId())
                && rooms.existsById(session.getCompletedRoomId());
    }

    private ReviewerDemoView create(String userId) {
        Room hostRoom = newRoom("[심사용] 경주 가을 산책", LocalDate.of(2026, 11, 7));
        Member host = members.save(Member.host(hostRoom.getId(), "운영진", userId));
        Member seoyeon = members.save(Member.join(hostRoom.getId(), "서연", FlowerCode.FORSYTHIA));
        seedPhoto(hostRoom, host, "첨성대", "경주 첨성대", "경주", 35.8346, 129.2191,
                "gyeongju-cheomseongdae-night.jpeg", "밤이 되자 첨성대가 분홍빛으로 물들어서 한참 발걸음을 멈췄어요.");
        seedPhoto(hostRoom, seoyeon, "월정교", "경주 월정교", "경주", 35.8299, 129.2147,
                "gyeongju-woljeonggyo-night.jpeg", "불빛을 따라 걷던 월정교가 물 위에 길게 비쳐서 밤 산책이 더 특별했어요.");
        seedPhoto(hostRoom, host, "황리단길 카페", "경주 황리단길", "경주", 35.8428, 129.2119,
                "gyeongju-cafe.jpeg", "잠시 쉬어 간 카페에서 케이크를 나눠 먹으며 다음에 갈 곳을 골랐어요.");
        hostRoom.startCirculating();

        Room memberRoom = newRoom("[심사용] 포항 바다의 밤", LocalDate.of(2026, 10, 25));
        Member minji = members.save(Member.host(memberRoom.getId(), "민지"));
        Member reviewer = members.save(Member.join(memberRoom.getId(), "운영진", FlowerCode.FORGET_ME_NOT, userId));
        Member junho = members.save(Member.join(memberRoom.getId(), "준호", FlowerCode.LILAC));
        seedPhoto(memberRoom, minji, "포항 횟집", "포항", "포항", 36.0190, 129.3435,
                "pohang-sashimi.jpeg", "여러 가지 회를 한 접시에 펼쳐 놓고, 누가 가장 먼저 젓가락을 들지 웃으며 기다렸어요.");
        seedPhoto(memberRoom, junho, "스페이스워크", "포항 스페이스워크", "포항", 36.0672, 129.3845,
                "pohang-space-walk.jpeg", "둥근 길 사이로 해가 지는 모습이 보여서 다 같이 한참 아래를 내려다봤어요.");
        seedPhoto(memberRoom, minji, "영일대 해수욕장", "포항 영일대 해수욕장", "포항", 36.0541, 129.3788,
                "pohang-seaside.jpeg", "바다 바로 앞 길을 걷다가 구름이 빠르게 지나가는 모습을 발견했어요.");
        seedPhoto(memberRoom, reviewer, "영일대 해수욕장", "포항 영일대 해수욕장", "포항", 36.0541, 129.3788,
                "pohang-beach.jpeg", "모래 위 발자국을 따라 걷다 보니 어느새 바다 가까이 와 있었어요.");
        memberRoom.startCirculating();

        Room completedRoom = newRoom("[심사용] 안동 하루 여행", LocalDate.of(2026, 10, 12));
        completedRoom.changeScrollTemplate("ANDONG");
        Member jiwon = members.save(Member.host(completedRoom.getId(), "지원"));
        Member completedReviewer = members.save(Member.join(completedRoom.getId(), "운영진", FlowerCode.CANOLA, userId));
        Photo completedPhoto = seedPhoto(completedRoom, completedReviewer, "하회탈 전시", "안동", "안동", 36.5391, 128.5175,
                "andong-hahoe-mask.jpeg", "커다란 하회탈 앞에서 웃는 얼굴을 따라 해 보며 한참 장난쳤어요.");
        seedPhoto(completedRoom, jiwon, "안동찜닭", "안동", "안동", 36.5668, 128.7294,
                "andong-jjimdak.jpeg", "따끈한 안동찜닭을 앞에 두고, 각자 좋아하는 재료부터 골라 먹었어요.");
        seedPhoto(completedRoom, completedReviewer, "월영교", "안동 월영교", "안동", 36.5737, 128.7210,
                "andong-wolyeonggyo-night.jpeg", "월영교의 불빛이 물결 위에 번지는 모습을 보며 천천히 하루를 마무리했어요.");
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
        ReviewerDemoSession session = sessions.save(ReviewerDemoSession.create(userId, hostRoom.getId(), memberRoom.getId(), completedRoom.getId(), CURRENT_SEED_VERSION));
        return view(session);
    }

    private Room newRoom(String title, LocalDate meetingDate) {
        Room room = Room.create(title, meetingDate, 4, RoomPlan.LITE);
        room.changePlace(new com.dandimate.durooduroo.room.dto.RoomPlace(title, null, 35.84, 129.21));
        return rooms.save(room);
    }

    private Photo seedPhoto(Room room, Member author, String place, String address, String city, double latitude, double longitude,
                            String imageAsset, String text) {
        String key = "reviewer-demo/" + room.getId() + "/" + UUID.randomUUID() + ".jpeg";
        storage.putBytes(key, imageBytes(imageAsset), "image/jpeg");
        Photo photo = Photo.pending(UUID.randomUUID().toString(), room.getId(), author.getId(), key, storage.locationFor(key), "image/jpeg", 0);
        photo.complete(UUID.randomUUID().toString().replace("-", ""));
        photo.locate(place, address, city, latitude, longitude);
        photo.publish();
        Photo saved = photos.save(photo);
        entries.save(MemoryEntry.confirm(room.getId(), saved.getId(), author.getId(), EntryKind.FIRST, "TEXT", null, null, text));
        return saved;
    }

    private byte[] imageBytes(String asset) {
        try (var input = new ClassPathResource("reviewer-demo/" + asset).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("심사용 사진 리소스를 읽을 수 없어요: " + asset, exception);
        }
    }

    private ReviewerDemoView view(ReviewerDemoSession session) {
        return new ReviewerDemoView(session.getHostRoomId(), session.getMemberRoomId(), session.getCompletedRoomId());
    }
}
