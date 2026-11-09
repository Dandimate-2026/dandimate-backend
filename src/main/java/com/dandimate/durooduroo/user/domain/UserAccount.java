package com.dandimate.durooduroo.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_provider_subject", columnNames = {"provider", "provider_user_id"}))
public class UserAccount {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 20) private String provider;
    @Column(name = "provider_user_id", nullable = false, length = 100) private String providerUserId;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(name = "withdrawn_at") private LocalDateTime withdrawnAt;
    protected UserAccount() {}
    public static UserAccount kakao(String subject) {
        UserAccount user = new UserAccount();
        user.id = UUID.randomUUID().toString(); user.provider = "KAKAO";
        user.providerUserId = subject; user.createdAt = LocalDateTime.now(); return user;
    }
    public String getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** Remove the provider link while retaining the account row for shared room history. */
    public void withdraw() {
        this.providerUserId = "WITHDRAWN-" + UUID.randomUUID();
        this.withdrawnAt = LocalDateTime.now();
    }
    public boolean isWithdrawn() { return withdrawnAt != null; }
}
