package com.dandimate.durooduroo.notification.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "push_subscriptions", uniqueConstraints = @UniqueConstraint(name = "uk_push_subscription_endpoint", columnNames = "endpoint"))
public class PushSubscription {
    @Id private String id;
    @Column(name = "user_id", nullable = false) private String userId;
    @Column(nullable = false, length = 1000) private String endpoint;
    @Column(nullable = false, length = 255) private String p256dh;
    @Column(nullable = false, length = 255) private String auth;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    protected PushSubscription() {}
    public static PushSubscription of(String userId, String endpoint, String p256dh, String auth) {
        PushSubscription value = new PushSubscription();
        value.id = UUID.randomUUID().toString(); value.userId = userId; value.endpoint = endpoint;
        value.p256dh = p256dh; value.auth = auth; value.createdAt = value.updatedAt = LocalDateTime.now(); return value;
    }
    public void refresh(String userId, String p256dh, String auth) { this.userId = userId; this.p256dh = p256dh; this.auth = auth; this.updatedAt = LocalDateTime.now(); }
    public String getEndpoint() { return endpoint; }
}
