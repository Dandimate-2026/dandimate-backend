/** 방에 참여한 사용자의 닉네임, 토큰 해시, 역할을 관리한다. */
package com.dandimate.durooduroo.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import com.dandimate.durooduroo.common.auth.Tokens;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_members_room_user", columnNames = {"room_id", "user_id"}),
                @UniqueConstraint(name = "uk_members_room_nickname", columnNames = {"room_id", "nickname"}),
                @UniqueConstraint(name = "uk_members_room_flower", columnNames = {"room_id", "flower_code"})
        }
)
public class Member implements Persistable<String> {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "room_id", nullable = false, length = 36, updatable = false)
    private String roomId;

    @Column(name = "user_id", length = 36, updatable = false)
    private String userId;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_members_user"))
    private com.dandimate.durooduroo.user.domain.UserAccount userAccount;

    @Column(nullable = false, length = 30)
    private String nickname;

    /** memberAccessToken 원문은 저장하지 않고 SHA-256 해시만 저장한다. */
    @Column(name = "access_token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String accessTokenHash;

    /** 입장 직후 응답에 한 번만 내보낼 원문. DB에는 저장되지 않는다. */
    @Transient
    private String issuedAccessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "flower_code", nullable = false, length = 20, updatable = false)
    private FlowerCode flowerCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Member() {
    }

    /** 직접 ID를 부여하므로 save()가 merge 대신 persist를 쓰도록 새 엔티티 여부를 알려 준다. */
    @Transient
    private boolean newEntity = true;

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    private Member(String roomId, String nickname, MemberRole role, FlowerCode flowerCode) {
        this.id = UUID.randomUUID().toString();
        this.roomId = roomId;

        this.nickname = nickname;
        this.issuedAccessToken = Tokens.generate();
        this.accessTokenHash = Tokens.hash(issuedAccessToken);
        this.role = role;
        this.flowerCode = flowerCode;
        this.createdAt = LocalDateTime.now();
    }

    public static Member join(String roomId, String nickname, FlowerCode flowerCode) {
        return new Member(roomId, nickname, MemberRole.MEMBER, flowerCode);
    }

    public static Member host(String roomId, String nickname) {
        return new Member(roomId, nickname, MemberRole.HOST, FlowerCode.AZALEA);
    }

    @Override
    public String getId() {
        return id;
    }

    public static Member host(String roomId, String nickname, String userId) {
        Member member = host(roomId, nickname); member.userId = userId; return member;
    }
    public static Member join(String roomId, String nickname, FlowerCode flower, String userId) {
        Member member = join(roomId, nickname, flower); member.userId = userId; return member;
    }

    public String getUserId() { return userId; }

    public String getRoomId() {
        return roomId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getIssuedAccessToken() {
        return userId == null ? issuedAccessToken : null;
    }

    public MemberRole getRole() {
        return role;
    }

    public FlowerCode getFlowerCode() {
        return flowerCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
