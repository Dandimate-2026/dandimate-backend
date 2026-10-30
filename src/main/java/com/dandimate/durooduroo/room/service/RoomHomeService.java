/**
 * 방 홈 화면 상태를 계산한다 (명세 1장 방 홈, FR-A9·A10·D5).
 * - photoCount: 올린 사진 수, storyCount: 남긴 이야기(FIRST·ADDON) 수
 * - responseRate: 그 참여자가 올린 노출 사진에 다른 참여자가 덧붙임·패스로 응답한 비율
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
        List<Member> roomMembers = members.findAllByRoomId(roomId).stream().sorted(Comparator.comparing(m -> m.getFlowerCode().ordinal())).toList();
        List<Photo> roomPhotos = photos.findAllByRoomId(roomId);
        List<Photo> published = roomPhotos.stream().filter(Photo::isPublished).toList();
        List<MemoryEntry> roomEntries = entries.findAllByRoomIdOrderByCreatedAtAsc(roomId);
        Set<String> responded = new HashSet<>();
        roomEntries.stream().filter(e -> e.getKind() == EntryKind.ADDON).forEach(e -> responded.add(e.getPhotoId() + ":" + e.getMemberId()));
        for (PhotoPass pass : passes.findAllByRoomId(roomId)) responded.add(pass.getPhotoId() + ":" + pass.getMemberId());

        List<RoomHomeView.MemberStats> stats = roomMembers.stream().map(m -> stats(m, roomMembers, roomPhotos, published, roomEntries, responded)).toList();
        RoomHomeView.MemberStats myStats = stats.stream().filter(s -> s.id().equals(me.getId())).findFirst().orElseThrow();

        LocalDate today = LocalDate.now();
        List<RoomHomeView.TodayFlower> todayFlowers = roomEntries.stream()
                .filter(e -> (e.getKind() == EntryKind.FIRST || e.getKind() == EntryKind.ADDON) && e.getCreatedAt().toLocalDate().equals(today))
                .map(e -> new RoomHomeView.TodayFlower(e.getMemberId(), flowerOf(e.getMemberId(), roomMembers), e.getKind().name()))
                .toList();

        boolean needsFirstEntry = roomPhotos.stream().anyMatch(p -> p.getMemberId().equals(me.getId()) && p.isReady() && !p.isPublished());
        int pending = (int) published.stream().filter(p -> !p.getMemberId().equals(me.getId()) && !responded.contains(p.getId() + ":" + me.getId())).count();
        RoomHomeView.TopAskback topAskback = askbacks.findAllByRoomIdAndTargetMemberId(roomId, me.getId()).stream()
                .filter(a -> Askback.PENDING.equals(a.getStatus()))
                .sorted(Comparator.comparing((Askback a) -> a.getPostponedAt() != null).thenComparing(Askback::getCreatedAt))
                .findFirst().map(a -> new RoomHomeView.TopAskback(a.getId(), a.getQuestion())).orElse(null);

        boolean allCompleted = !published.isEmpty() && roomMembers.stream().allMatch(m -> published.stream()
                .filter(p -> !p.getMemberId().equals(m.getId()))
                .allMatch(p -> responded.contains(p.getId() + ":" + m.getId())));

        return new RoomHomeView(
                new RoomHomeView.RoomSummary(room.getId(), room.getTitle(), room.getMeetingDate(), room.getStatus().name(), room.getDeadlineAt()),
                myStats, stats, todayFlowers, new RoomHomeView.MyTodo(needsFirstEntry, pending, topAskback),
                notifications.countByMemberIdAndReadAtIsNull(me.getId()), allCompleted);
    }

    private RoomHomeView.MemberStats stats(Member member, List<Member> roomMembers, List<Photo> roomPhotos, List<Photo> published,
                                           List<MemoryEntry> roomEntries, Set<String> responded) {
        int photoCount = (int) roomPhotos.stream().filter(p -> p.getMemberId().equals(member.getId())).count();
        int storyCount = (int) roomEntries.stream()
                .filter(e -> e.getMemberId().equals(member.getId()) && (e.getKind() == EntryKind.FIRST || e.getKind() == EntryKind.ADDON)).count();
        List<Photo> myPublished = published.stream().filter(p -> p.getMemberId().equals(member.getId())).toList();
        List<Member> others = roomMembers.stream().filter(m -> !m.getId().equals(member.getId())).toList();
        int possible = myPublished.size() * others.size();
        long answered = myPublished.stream().mapToLong(p -> others.stream().filter(o -> responded.contains(p.getId() + ":" + o.getId())).count()).sum();
        double responseRate = possible == 0 ? 0.0 : (double) answered / possible;
        return new RoomHomeView.MemberStats(member.getId(), member.getNickname(), member.getRole().name(), member.getFlowerCode().name(),
                photoCount, storyCount, responseRate);
    }

    private String flowerOf(String memberId, List<Member> roomMembers) {
        return roomMembers.stream().filter(m -> m.getId().equals(memberId)).findFirst().map(m -> m.getFlowerCode().name()).orElse(null);
    }
}
