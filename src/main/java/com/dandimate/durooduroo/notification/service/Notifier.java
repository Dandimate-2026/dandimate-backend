/** 앱 내 알림을 저장하고 대상자 스트림에 notification.created를 보낸다. */
package com.dandimate.durooduroo.notification.service;

import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class Notifier {

    private final NotificationRepository notifications;
    private final RoomEvents events;

    public Notifier(NotificationRepository notifications, RoomEvents events) {
        this.notifications = notifications;
        this.events = events;
    }

    public Notification notify(String roomId, String memberId, String type, String resourceType, String resourceId) {
        Notification notification = notifications.save(Notification.create(memberId, type, resourceType, resourceId));
        events.toMember(roomId, memberId, "notification.created", Map.of("notificationId", notification.getId(), "type", type));
        return notification;
    }
}
