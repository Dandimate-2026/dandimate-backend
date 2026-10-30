/** 방의 식별자, 초대 코드, 제목, 상태와 상태 전이 규칙을 관리한다. */
package com.dandimate.durooduroo.room.domain;

import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rooms_invite_token_hash",
                columnNames = "invite_token_hash"
        )
)
public class Room implements Persistable<String> {

    public static final int CAPACITY = 8;
    public static final int REGENERATION_LIMIT = 10;
    private static final int DEFAULT_DEADLINE_HOURS = 48;
    private static final int AUTO_EXTENSION_HOURS = 24;

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String title;

    private LocalDate meetingDate;

    /** 초대 토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다. */
    @Column(name = "invite_token_hash", nullable = false, length = 64)
    private String inviteTokenHash;

    /** 생성·재발급 직후 응답에 한 번만 내보낼 원문. DB에는 저장되지 않는다. */
    @Transient
    private String issuedInviteToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(nullable = false)
    private LocalDateTime deadlineAt;

    @Column(nullable = false)
    private int regenerationCount;

    /** 원문 없이 마감 시각이 지나 자동 연장한 적이 있는지. 방당 1회만 연장한다. */
    @Column(nullable = false)
    private boolean deadlineExtended;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Room() {
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

    private Room(String title, LocalDate meetingDate) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        rotateInviteToken();
        this.meetingDate = meetingDate;
        this.status = RoomStatus.COLLECTING;
        this.createdAt = LocalDateTime.now();
        this.deadlineAt = createdAt.plusHours(DEFAULT_DEADLINE_HOURS);
    }

    public static Room create(String title) {
        return new Room(title, null);
    }

    public static Room create(String title, LocalDate meetingDate) {
        return new Room(title, meetingDate);
    }

    /** 상태별 쓰기 허용 표를 어기면 WEAVING은 ROOM_WEAVING, 그 외는 INVALID_ROOM_STATE로 거절한다. */
    public void requireAllows(RoomAction action) {
        if (action.allows(status)) return;
        if (status == RoomStatus.WEAVING) {
            throw new BusinessException(ErrorCode.ROOM_WEAVING, "두루마리를 엮는 중에는 할 수 없어요.");
        }
        throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "지금 방 상태에서는 할 수 없어요.");
    }

    /** 첫 한마디가 확정되면 COLLECTING → CIRCULATING. */
    public void startCirculating() {
        if (status == RoomStatus.COLLECTING) this.status = RoomStatus.CIRCULATING;
    }

    /** 마감: CIRCULATING → WEAVING. */
    public void startWeaving() {
        requireAllowsTransition(RoomStatus.CIRCULATING);
        this.status = RoomStatus.WEAVING;
    }

    /** 생성 성공: WEAVING → GROWING. GROWING에서 다시 엮은 경우는 그대로 둔다. */
    public void completeWeaving() {
        if (status == RoomStatus.WEAVING) this.status = RoomStatus.GROWING;
    }

    /** COLLECTING에서 마감 시각이 지났는데 공개 원문이 없으면 한 번만 24시간 연장한다. 이미 연장했으면 false. */
    public boolean extendDeadlineOnce() {
        if (status != RoomStatus.COLLECTING || deadlineExtended) return false;
        deadlineExtended = true;
        deadlineAt = deadlineAt.plusHours(AUTO_EXTENSION_HOURS);
        return true;
    }

    /** 연장 후에도 원문이 없으면 AI 작업 없이 빈 두루마리 안내 상태(GROWING)로 끝낸다. */
    public void completeWithoutScroll() {
        if (status != RoomStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "모으는 중인 방만 빈 상태로 마감할 수 있어요.");
        }
        status = RoomStatus.GROWING;
    }

    public boolean isDeadlineExtended() {
        return deadlineExtended;
    }

    /** 수동 다시 엮기·답가 재엮기 한도(방당 10회)를 사용한다. */
    public void useRegeneration() {
        if (status != RoomStatus.WEAVING && status != RoomStatus.GROWING) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "마감 후에만 다시 엮을 수 있어요.");
        }
        if (regenerationCount >= REGENERATION_LIMIT) {
            throw new BusinessException(ErrorCode.LIMIT_REACHED, "다시 엮기 한도를 모두 사용했어요.");
        }
        regenerationCount++;
    }

    private void requireAllowsTransition(RoomStatus from) {
        if (status == from) return;
        if (status == RoomStatus.WEAVING) {
            throw new BusinessException(ErrorCode.ROOM_WEAVING, "이미 두루마리를 엮는 중이에요.");
        }
        throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "지금 방 상태에서는 마감할 수 없어요.");
    }

    /** 새 초대 토큰을 발급한다. 기존 토큰은 해시가 바뀌므로 즉시 무효가 된다. */
    public String rotateInviteToken() {
        this.issuedInviteToken = Tokens.generate();
        this.inviteTokenHash = Tokens.hash(issuedInviteToken);
        return issuedInviteToken;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getMeetingDate() {
        return meetingDate;
    }

    public String getIssuedInviteToken() {
        return issuedInviteToken;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public int getRegenerationCount() {
        return regenerationCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
