/** 방에 참여한 사용자의 닉네임, 토큰, 역할을 관리한다. */
package com.dandimate.durooduroo.member.domain;

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
        name = "members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_members_room_nickname",
                columnNames = {"room_id", "nickname"}
        )
)
public class Member {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "room_id", nullable = false, length = 36, updatable = false)
    private String roomId;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(name = "member_token", nullable = false, unique = true, length = 36, updatable = false)
    private String memberToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Member() {
    }

    private Member(String roomId, String nickname, MemberRole role) {
        this.id = UUID.randomUUID().toString();
        this.roomId = roomId;
        this.nickname = nickname;
        this.memberToken = UUID.randomUUID().toString();
        this.role = role;
        this.createdAt = LocalDateTime.now();
    }

    public static Member join(String roomId, String nickname) {
        return new Member(roomId, nickname, MemberRole.MEMBER);
    }

    public static Member host(String roomId, String nickname) {
        return new Member(roomId, nickname, MemberRole.HOST);
    }

    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getMemberToken() {
        return memberToken;
    }

    public MemberRole getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
