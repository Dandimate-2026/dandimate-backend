/** 방의 식별자, 초대 코드, 제목, 상태와 상태 전이 규칙을 관리한다. */
package com.dandimate.durooduroo.room.domain;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
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

    public static final int CAPACITY = 8;
    public static final int REGENERATION_LIMIT = 10;
    private static final int DEFAULT_DEADLINE_HOURS = 48;

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String title;

    private LocalDate meetingDate;

    @Column(name = "invite_code", nullable = false, length = 6)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(nullable = false)
    private LocalDateTime deadlineAt;

    @Column(nullable = false)
    private int regenerationCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Room() {
    }

    private Room(String title, String inviteCode, LocalDate meetingDate) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.inviteCode = inviteCode;
        this.meetingDate = meetingDate;
        this.status = RoomStatus.COLLECTING;
        this.createdAt = LocalDateTime.now();
        this.deadlineAt = createdAt.plusHours(DEFAULT_DEADLINE_HOURS);
    }

    public static Room create(String title, String inviteCode) {
        return new Room(title, inviteCode, null);
    }

    public static Room create(String title, String inviteCode, LocalDate meetingDate) {
        return new Room(title, inviteCode, meetingDate);
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

    public void rotateInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getMeetingDate() {
        return meetingDate;
    }

    public String getInviteCode() {
        return inviteCode;
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
