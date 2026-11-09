/** 앱 내 알림을 저장하고 대상자 스트림에 notification.created를 보낸다. */
package com.dandimate.durooduroo.notification.service;

import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class Notifier {

    private final NotificationRepository notifications;
    private final RoomEvents events;
    private final ApplicationEventPublisher publisher;

    public Notifier(NotificationRepository notifications, RoomEvents events, ApplicationEventPublisher publisher) {
        this.notifications = notifications;
        this.events = events;
        this.publisher = publisher;
    }

    public Notification notify(String roomId, String memberId, String type, String resourceType, String resourceId) {
        return notify(roomId, memberId, type, resourceType, resourceId, null);
    }

    public Notification notify(String roomId, String memberId, String type, String resourceType, String resourceId, String message) {
        Notification notification = notifications.save(Notification.create(memberId, type, resourceType, resourceId, message));
        events.toMember(roomId, memberId, "notification.created", Map.of("notificationId", notification.getId(), "type", type, "resourceId", resourceId));
        publisher.publishEvent(new PushNotificationEvent(notification, roomId));
        return notification;
    }

    public Notification notifyScheduled(String roomId, String memberId, String type, String resourceType, String resourceId,
                                        String message, java.time.LocalDateTime scheduledFor) {
        Notification notification = notifications.save(Notification.scheduled(memberId, type, resourceType, resourceId, message, scheduledFor));
        events.toMember(roomId, memberId, "notification.created", Map.of("notificationId", notification.getId(), "type", type));
        publisher.publishEvent(new PushNotificationEvent(notification, roomId));
        return notification;
    }
}
