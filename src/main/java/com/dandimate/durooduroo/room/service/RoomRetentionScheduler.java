/** 방 생성 완료 후 1년 보존 정책: 30·7·1일 전 알림을 만들고 만료된 방과 저장소 파일을 삭제한다. */
package com.dandimate.durooduroo.room.service;

import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import com.dandimate.durooduroo.notification.service.Notifier;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RoomRetentionScheduler {
    private static final Logger log = LoggerFactory.getLogger(RoomRetentionScheduler.class);
    private static final List<Integer> WARNING_DAYS = List.of(30, 7, 1);
    private final RoomRepository rooms;
    private final MemberRepository members;
    private final NotificationRepository notifications;
    private final RoomAdminService admin;
    private final Notifier notifier;

    public RoomRetentionScheduler(RoomRepository rooms, MemberRepository members, NotificationRepository notifications, RoomAdminService admin, Notifier notifier) {
        this.rooms = rooms; this.members = members; this.notifications = notifications; this.admin = admin; this.notifier = notifier;
    }

    @Scheduled(cron = "${app.rooms.retention-cron:0 15 * * * *}")
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        rooms.findAllByCreatedAtLessThanEqual(now.minusYears(1).plusDays(30)).forEach(room -> {
            try {
                if (!room.getDeleteAt().isAfter(now)) admin.deleteExpired(room.getId(), now);
                else createWarnings(room, now);
            } catch (RuntimeException e) {
                log.warn("Room retention failed for {}: {}", room.getId(), e.getClass().getSimpleName());
            }
        });
    }

    @Transactional
    public void createWarnings(Room room, LocalDateTime now) {
        for (int days : WARNING_DAYS) {
            if (room.getDeleteAt().minusDays(days).isAfter(now)) continue;
            String type = "ROOM_DELETION_DUE_" + days + "D";
            String message = "'" + room.getTitle() + "' 모임방이 " + room.getDeleteAt().toLocalDate() + "에 자동 삭제돼요.";
            members.findAllByRoomIdAndLeftAtIsNull(room.getId()).forEach(member -> {
                if (!notifications.existsByMemberIdAndTypeAndResourceId(member.getId(), type, room.getId()))
                    notifier.notifyScheduled(room.getId(), member.getId(), type, "ROOM", room.getId(), message, room.getDeleteAt());
            });
        }
    }
}
