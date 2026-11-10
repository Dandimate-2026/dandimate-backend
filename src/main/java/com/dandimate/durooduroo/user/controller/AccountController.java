package com.dandimate.durooduroo.user.controller;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.user.repository.UserAccountRepository;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.memory.repository.PhotoPassRepository;
import com.dandimate.durooduroo.memory.domain.EntryKind;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final UserAccountRepository users;
    private final MemberRepository members;
    private final RoomRepository rooms;
    private final NotificationRepository notifications;
    private final PhotoRepository photos;
    private final MemoryEntryRepository entries;
    private final PhotoPassRepository passes;
    private final com.dandimate.durooduroo.user.service.AccountWithdrawalService withdrawal;
    public AccountController(UserAccountRepository users, MemberRepository members, RoomRepository rooms, NotificationRepository notifications, PhotoRepository photos, MemoryEntryRepository entries, PhotoPassRepository passes, com.dandimate.durooduroo.user.service.AccountWithdrawalService withdrawal) {
        this.users=users; this.members=members; this.rooms=rooms; this.notifications=notifications; this.photos=photos; this.entries=entries; this.passes=passes; this.withdrawal=withdrawal;
    }
    @GetMapping("/auth/csrf")
    public ApiResponse<?> csrf(CsrfToken token) {
        return ApiResponse.success(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
    @GetMapping("/users/me")
    public ApiResponse<?> me() {
        var user = users.findById(CurrentUser.requireId()).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_MEMBER_TOKEN, "다시 로그인해 주세요."));
        return ApiResponse.success(Map.of("id", user.getId(), "createdAt", user.getCreatedAt()));
    }
    @DeleteMapping("/users/me")
    public org.springframework.http.ResponseEntity<Void> withdraw(HttpSession session) {
        withdrawal.withdraw(CurrentUser.requireId());
        session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        return org.springframework.http.ResponseEntity.noContent().build();
    }
    @GetMapping("/users/me/rooms")
    public ApiResponse<?> rooms() {
        var participation = members.findAllByUserIdAndLeftAtIsNull(CurrentUser.requireId());
        var byId = rooms.findAllById(participation.stream().map(m -> m.getRoomId()).toList()).stream()
            .collect(java.util.stream.Collectors.toMap(r -> r.getId(), r -> r));
        return ApiResponse.success(participation.stream().filter(m -> byId.containsKey(m.getRoomId())).map(m -> {
            var room = byId.get(m.getRoomId());
            var result = new java.util.LinkedHashMap<String,Object>(Map.of("roomId", room.getId(), "name", room.getTitle(), "status", room.getStatus(),
                "memberId", m.getId(), "nickname", m.getNickname(), "role", m.getRole()));
            result.put("place", room.getPlace());
            result.put("coverImageUrl", room.getCoverImageUrl());
            result.put("coverPhoto", com.dandimate.durooduroo.room.dto.CoverPhotoView.from(room.getCoverPhotoId()));
            result.put("meetingDate", room.getMeetingDate());
            result.put("deadlineAt", room.getDeadlineAt());
            result.put("unreadCount", notifications.countByMemberIdAndReadAtIsNull(m.getId()));
            result.put("unreadReplyCount", notifications.countByMemberIdAndReadAtIsNullAndTypeStartingWith(m.getId(), "REPLY"));
            var roomPhotos = photos.findAllByRoomId(room.getId());
            var posted = roomPhotos.stream().filter(com.dandimate.durooduroo.photo.domain.Photo::isPublished).toList();
            var responded = new java.util.HashSet<String>();
            entries.findAllByRoomId(room.getId()).stream().filter(e -> e.getKind() == EntryKind.ADDON && m.getId().equals(e.getMemberId())).forEach(e -> responded.add(e.getPhotoId()));
            passes.findAllByRoomIdAndMemberId(room.getId(), m.getId()).forEach(p -> responded.add(p.getPhotoId()));
            boolean needsFirstEntry = roomPhotos.stream().anyMatch(p -> m.getId().equals(p.getMemberId()) && p.isReady() && !p.isPublished());
            int pendingCirculationCount = (int) posted.stream().filter(p -> !m.getId().equals(p.getMemberId()) && !responded.contains(p.getId())).count();
            result.put("myTodo", Map.of("needsFirstEntry", needsFirstEntry, "pendingCirculationCount", pendingCirculationCount));
            return result;
        }).toList());
    }
}
