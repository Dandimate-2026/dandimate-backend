/** 방의 식별자, 초대 코드, 제목, 상태를 관리한다. */
package com.dandimate.durooduroo.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rooms_invite_code",
                columnNames = "invite_code"
        )
)
public class Room {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "invite_code", nullable = false, length = 6)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Room() {
    }

    private Room(String title, String inviteCode) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.inviteCode = inviteCode;
        this.status = RoomStatus.OPEN;
        this.createdAt = LocalDateTime.now();
    }

    public static Room create(String title, String inviteCode) {
        return new Room(title, inviteCode);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void close() { this.status = RoomStatus.CLOSED; }
    public void rotateInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
