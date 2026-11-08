/**
 * 방 홈 화면 상태를 계산한다 (명세 1장 방 홈, FR-A9·A10·D5).
 * - photoCount: 올린 사진 수, storyCount: 남긴 이야기(FIRST·ADDON) 수
 * - responseRate: 그 참여자가 다른 참여자의 노출 사진 중 덧붙임·패스로 응답한 비율 (응답할 사진이 없으면 0)
 * - todayFlowers: 오늘(서버 시간 기준) 확정된 이야기 하나당 꽃 하나, 확정 순서대로
 * - myTodo: 첫 한마디가 필요한 내 사진이 있는지, 응답하지 않은 타인 사진 수, 미응답 되묻기 하나(미룬 적 없는 것 먼저)
 * - allCompleted: 노출된 사진이 있고, 모든 참여자가 모든 타인 사진에 덧붙임 또는 패스를 남겼는지 (되묻기 답은 제외)
 */
package com.dandimate.durooduroo.room.service;

import com.dandimate.durooduroo.askback.domain.Askback;
import com.dandimate.durooduroo.askback.repository.AskbackRepository;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.EntryKind;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.domain.PhotoPass;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.memory.repository.PhotoPassRepository;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.dto.RoomHomeView;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomHomeService {

    private static final java.time.ZoneId SERVICE_ZONE = java.time.ZoneId.of("Asia/Seoul");

    private final RoomRepository rooms;
    private final MemberRepository members;
    private final PhotoRepository photos;
    private final MemoryEntryRepository entries;
    private final PhotoPassRepository passes;
    private final AskbackRepository askbacks;
    private final NotificationRepository notifications;

    public RoomHomeService(RoomRepository rooms, MemberRepository members, PhotoRepository photos, MemoryEntryRepository entries,
                           PhotoPassRepository passes, AskbackRepository askbacks, NotificationRepository notifications) {
        this.rooms = rooms;
        this.members = members;
        this.photos = photos;
        this.entries = entries;
        this.passes = passes;
        this.askbacks = askbacks;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public RoomHomeView home(String roomId, String authorization) {
        Room room = rooms.findById(roomId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "존재하지 않는 방입니다."));
        Member me = members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 조회할 수 있습니다."));
        List<Member> roomMembers = members.findAllByRoomIdAndLeftAtIsNull(roomId).stream().sorted(Comparator.comparing(m -> m.getFlowerCode().ordinal())).toList();
        var uploadCutoff = java.time.LocalDateTime.now().minus(com.dandimate.durooduroo.storage.MediaUrls.UPLOAD_TTL);
        List<Photo> roomPhotos = photos.findAllByRoomId(roomId).stream().filter(p -> !p.isAbandonedUpload(uploadCutoff)).toList();
        List<Photo> published = roomPhotos.stream().filter(Photo::isPublished).toList();
        List<MemoryEntry> roomEntries = entries.findAllByRoomIdOrderByCreatedAtAsc(roomId);
        Set<String> responded = responded(roomId, roomEntries);

        List<Photo> participationPhotos = roomPhotos.stream().filter(Photo::isReady).toList();
        List<RoomHomeView.MemberStats> stats = roomMembers.stream().map(m -> stats(m, roomPhotos, participationPhotos, roomEntries, responded)).toList();
        RoomHomeView.MemberStats myStats = stats.stream().filter(s -> s.id().equals(me.getId())).findFirst().orElseThrow();

        List<Member> allMembers = members.findAllByRoomId(roomId);
        // 이름은 이전 앱 호환 때문에 todayFlowers로 유지하지만, 정책상 날짜 제한 없이 모든 핀 꽃을 내려준다.
        List<RoomHomeView.TodayFlower> todayFlowers = roomEntries.stream()
                .filter(e -> e.getKind() == EntryKind.FIRST || e.getKind() == EntryKind.ADDON)
                .map(e -> new RoomHomeView.TodayFlower(e.getMemberId(), flowerOf(e.getMemberId(), allMembers), e.getKind().name()))
                .toList();

        boolean needsFirstEntry = roomPhotos.stream().anyMatch(p -> p.getMemberId().equals(me.getId()) && p.isReady() && !p.isPublished());
        int pending = (int) published.stream().filter(p -> !p.getMemberId().equals(me.getId()) && !responded.contains(p.getId() + ":" + me.getId())).count();
        RoomHomeView.TopAskback topAskback = askbacks.findAllByRoomIdAndTargetMemberId(roomId, me.getId()).stream()
                .filter(a -> Askback.PENDING.equals(a.getStatus()) && a.getExpiresAt().isAfter(java.time.LocalDateTime.now()))
                .filter(a -> passes.findByPhotoIdAndMemberId(a.getPhotoId(), me.getId()).isPresent())
                .filter(a -> entries.countByPhotoIdAndMemberIdAndKind(a.getPhotoId(), me.getId(), EntryKind.ADDON) == 0)
                .sorted(Comparator.comparing((Askback a) -> a.getPostponedAt() != null).thenComparing(Askback::getCreatedAt))
                .findFirst().map(a -> new RoomHomeView.TopAskback(a.getId(), a.getQuestion())).orElse(null);

        boolean allCompleted = completed(roomMembers, published, responded);

        return new RoomHomeView(
                new RoomHomeView.RoomSummary(room.getId(), room.getTitle(), room.getMeetingDate(), room.getStatus().name(), room.getDeadlineAt(), room.getCapacity(), room.getPlanType().name(), room.getScrollTemplateId(),
                        com.dandimate.durooduroo.room.dto.RoomQuotaView.from(room, roomPhotos.size(), myStats.photoCount()), room.getPlace(), com.dandimate.durooduroo.room.dto.CoverPhotoView.from(room.getCoverPhotoId()), room.getCoverImageUrl(), room.getCreatedAt(), room.getDeleteAt(), room.isDeadlineChangedByHost()),
                myStats, stats, todayFlowers, new RoomHomeView.MyTodo(needsFirstEntry, pending, topAskback),
                notifications.countByMemberIdAndReadAtIsNull(me.getId()), allCompleted);
    }

    /** 전원 완료 여부 (all_members_completed 이벤트 판정에도 쓴다). */
    @Transactional(readOnly = true)
    public boolean allCompleted(String roomId) {
        List<Photo> published = photos.findAllByRoomId(roomId).stream().filter(Photo::isPublished).toList();
        return completed(members.findAllByRoomIdAndLeftAtIsNull(roomId), published, responded(roomId, entries.findAllByRoomIdOrderByCreatedAtAsc(roomId)));
    }

    /** "사진ID:참여자ID" 형태로 덧붙임·패스 응답을 모은다. 되묻기 답은 응답으로 치지 않는다. */
    private Set<String> responded(String roomId, List<MemoryEntry> roomEntries) {
        Set<String> responded = new HashSet<>();
        roomEntries.stream().filter(e -> e.getKind() == EntryKind.ADDON).forEach(e -> responded.add(e.getPhotoId() + ":" + e.getMemberId()));
        for (PhotoPass pass : passes.findAllByRoomId(roomId)) responded.add(pass.getPhotoId() + ":" + pass.getMemberId());
        return responded;
    }

    private boolean completed(List<Member> roomMembers, List<Photo> published, Set<String> responded) {
        return !published.isEmpty() && roomMembers.stream().allMatch(m -> published.stream()
                .filter(p -> !p.getMemberId().equals(m.getId()))
                .allMatch(p -> responded.contains(p.getId() + ":" + m.getId())));
    }

    /** 서버 시간대와 관계없이 한국 날짜로 비교한다. */
    static boolean isOn(java.time.LocalDateTime serverTime, LocalDate koreanDate) {
        return serverTime.atZone(java.time.ZoneId.systemDefault()).withZoneSameInstant(SERVICE_ZONE).toLocalDate().equals(koreanDate);
    }

    private RoomHomeView.MemberStats stats(Member member, List<Photo> roomPhotos, List<Photo> participationPhotos,
                                           List<MemoryEntry> roomEntries, Set<String> responded) {
        int photoCount = (int) roomPhotos.stream().filter(p -> p.getMemberId().equals(member.getId())).count();
        int storyCount = (int) roomEntries.stream()
                .filter(e -> e.getMemberId().equals(member.getId()) && (e.getKind() == EntryKind.FIRST || e.getKind() == EntryKind.ADDON)).count();
        Set<String> participatedPhotoIds = new HashSet<>();
        participationPhotos.stream().filter(p -> p.getMemberId().equals(member.getId())).map(Photo::getId).forEach(participatedPhotoIds::add);
        roomEntries.stream()
                .filter(e -> e.getMemberId().equals(member.getId()) && (e.getKind() == EntryKind.FIRST || e.getKind() == EntryKind.ADDON))
                .map(MemoryEntry::getPhotoId).forEach(participatedPhotoIds::add);
        // 참여율 = 방의 완료된 전체 사진 중 이 사람이 올리거나 이야기를 남긴 고유 사진의 비율. 패스는 참여로 세지 않는다.
        participatedPhotoIds.retainAll(participationPhotos.stream().map(Photo::getId).collect(java.util.stream.Collectors.toSet()));
        double responseRate = participationPhotos.isEmpty() ? 0.0 : (double) participatedPhotoIds.size() / participationPhotos.size();
        return new RoomHomeView.MemberStats(member.getId(), member.getNickname(), member.getRole().name(), member.getFlowerCode().name(),
                photoCount, storyCount, participatedPhotoIds.size(), participationPhotos.size(), responseRate);
    }

    private String flowerOf(String memberId, List<Member> roomMembers) {
        return roomMembers.stream().filter(m -> m.getId().equals(memberId)).findFirst().map(m -> m.getFlowerCode().name()).orElse(null);
    }
}
