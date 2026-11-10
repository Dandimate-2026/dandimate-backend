package com.dandimate.durooduroo.notification.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class NotificationController {
    private final NotificationRepository notifications;
    private final MemberRepository members;
    private final RoomRepository rooms;

    public NotificationController(NotificationRepository notifications, MemberRepository members, RoomRepository rooms) {
        this.notifications = notifications;
        this.members = members;
        this.rooms = rooms;
    }

    @GetMapping("/api/v1/notifications")
    public ApiResponse<?> all() {
        var memberships = members.findAllByUserIdAndLeftAtIsNull(CurrentUser.requireId());
        if (memberships.isEmpty()) return ApiResponse.success(List.of());
        var memberById = memberships.stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        var roomNames = new HashMap<String, String>();
        rooms.findAllById(memberships.stream().map(Member::getRoomId).distinct().toList())
                .forEach(room -> roomNames.put(room.getId(), room.getTitle()));
        var items = notifications.findAllByMemberIdInOrderByCreatedAtDesc(memberById.keySet()).stream()
                .map(notification -> view(notification, memberById.get(notification.getMemberId()), roomNames))
                .toList();
        return ApiResponse.success(items);
    }

    @GetMapping("/api/v1/rooms/{roomId}/notifications")
    public ApiResponse<?> list(@PathVariable String roomId, @RequestHeader(required = false) String authorization) {
        String token = MemberTokenResolver.resolve(authorization);
        var me = members.findByRoomIdAndMemberToken(roomId, token)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "\uCC38\uC5EC\uC790\uB9CC\uC54C\uB9BC\uC744\uC870\uD68C\uD560\uC218\uC788\uC2B5\uB2C8\uB2E4\u002E"));
        var roomNames = Map.of(roomId, rooms.findById(roomId).map(room -> room.getTitle()).orElse(""));
        var items = notifications.findAllByMemberIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(notification -> view(notification, me, roomNames)).toList();
        return ApiResponse.success(items);
    }

    @PostMapping("/api/v1/notifications/{notificationId}/read")
    public ApiResponse<?> read(@PathVariable String notificationId, @RequestHeader(required = false) String authorization) {
        var notification = notifications.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "\uC54C\uB9BC\uC774\uC5C6\uC2B5\uB2C8\uB2E4\u002E"));
        String token = MemberTokenResolver.resolve(authorization);
        var target = members.findById(notification.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "\uBCF8\uC778\uC54C\uB9BC\uB9CC\uC77D\uC744\uC218\uC788\uC2B5\uB2C8\uB2E4\u002E"));
        var me = members.findByRoomIdAndMemberToken(target.getRoomId(), token)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "\uCC38\uC5EC\uC790\uB9CC\uC54C\uB9BC\uC744\uC870\uD68C\uD560\uC218\uC788\uC2B5\uB2C8\uB2E4\u002E"));
        if (!me.getId().equals(notification.getMemberId())) throw new BusinessException(ErrorCode.FORBIDDEN, "\uBCF8\uC778\uC54C\uB9BC\uB9CC\uC77D\uC744\uC218\uC788\uC2B5\uB2C8\uB2E4\u002E");
        notification.read();
        notifications.save(notification);
        return ApiResponse.success(Map.of("id", notification.getId(), "read", true));
    }

    @DeleteMapping("/api/v1/notifications/{notificationId}")
    public ResponseEntity<Void> delete(@PathVariable String notificationId, @RequestHeader(required = false) String authorization) {
        var notification = notifications.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "알림이 없습니다."));
        String token = MemberTokenResolver.resolve(authorization);
        var target = members.findById(notification.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "본인 알림만 삭제할 수 있습니다."));
        var me = members.findByRoomIdAndMemberToken(target.getRoomId(), token)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
        if (!me.getId().equals(notification.getMemberId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 알림만 삭제할 수 있습니다.");
        }
        notifications.delete(notification);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> view(Notification notification, Member member, Map<String, String> roomNames) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", notification.getId());
        item.put("type", notification.getType());
        item.put("resourceType", notification.getResourceType());
        item.put("resourceId", notification.getResourceId());
        item.put("roomId", member.getRoomId());
        item.put("roomName", roomNames.getOrDefault(member.getRoomId(), ""));
        item.put("message", notification.getMessage());
        item.put("scheduledFor", notification.getScheduledFor());
        item.put("readAt", notification.getReadAt());
        item.put("createdAt", notification.getCreatedAt());
        return item;
    }
}
