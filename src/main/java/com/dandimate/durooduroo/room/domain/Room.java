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
@org.hibernate.annotations.DynamicUpdate
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rooms_invite_token_hash",
                columnNames = "invite_token_hash"
        )
)
public class Room implements Persistable<String> {

    @Column(length=200) private String placeName;
    @Column(length=500) private String placeAddress;
    private Double placeLatitude;
    private Double placeLongitude;
    @Column(length=36) private String coverPhotoId;
    public com.dandimate.durooduroo.room.dto.RoomPlace getPlace() {
        return placeName==null?null:new com.dandimate.durooduroo.room.dto.RoomPlace(placeName,placeAddress,placeLatitude,placeLongitude);
    }
    public void changePlace(com.dandimate.durooduroo.room.dto.RoomPlace p) {
        placeName=p==null?null:p.placeName();placeAddress=p==null?null:p.address();
        placeLatitude=p==null?null:p.latitude();placeLongitude=p==null?null:p.longitude();
    }
    public String getCoverPhotoId(){return coverPhotoId;}
    public void changeCoverPhoto(String id){coverPhotoId=id;}
    public static final int CAPACITY = 8;
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("8")
    private int capacity = CAPACITY;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10)")
    @org.hibernate.annotations.ColumnDefault("'FREE'")
    private RoomPlan planType = RoomPlan.FREE;
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long voiceUsedMs;
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long voiceReservedMs;
    /** 지도에 보여 줄 두루마리 한 줄 요약. 엮기 전이나 요약 실패 시 null. */
    @Column(length = 200)
    private String scrollSummary;

    public String getScrollSummary() { return scrollSummary; }
    public void summarizeScroll(String summary) { this.scrollSummary = summary; }

    public int getCapacity() { return capacity; }
    public boolean isDeadlineChangedByHost() { return deadlineChangedByHost; }
    public String getCoverImageUrl() { return coverPhotoId == null ? null : "/api/v1/room-covers/" + coverPhotoId + "/image"; }
    public void changeCapacity(int value, long activeMembers) {
        if (status != RoomStatus.COLLECTING && status != RoomStatus.CIRCULATING)
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "엮기 전까지만 정원을 바꿀 수 있어요.");
        if (value < 1 || value > CAPACITY)
            throw new BusinessException(ErrorCode.INVALID_CAPACITY, "정원은 1~8명이어야 해요.");
        if (value < activeMembers)
            throw new BusinessException(ErrorCode.INVALID_CAPACITY, "지금 들어온 " + activeMembers + "명보다 적게는 줄일 수 없어요.");
        capacity = value;
    }
    public RoomPlan getPlanType() { return planType; }
    public long getVoiceUsedMs() { return voiceUsedMs; }
    public long getVoiceReservedMs() { return voiceReservedMs; }
    public long getVoiceRemainingMs() { return Math.max(0, planType.voiceLimitMs() - voiceUsedMs - voiceReservedMs); }

    public static Room create(String title, LocalDate meetingDate, int capacity) {
        return create(title, meetingDate, capacity, RoomPlan.FREE);
    }

    /** 유료 플랜을 전달하는 호출부는 먼저 결제를 검증해야 한다. */
    public static Room create(String title, LocalDate meetingDate, int capacity, RoomPlan planType) {
        java.util.Objects.requireNonNull(planType, "planType");
        if (capacity < 1 || capacity > CAPACITY)
            throw new BusinessException(ErrorCode.INVALID_CAPACITY, "정원은 방장 포함 1~8명이어야 합니다.");
        Room room = new Room(title, meetingDate);
        room.capacity = capacity;
        room.planType = planType;
        return room;
    }

    public void upgradePlan(RoomPlan target) {
        if (target.priceKrw() <= planType.priceKrw())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "상위 플랜으로만 변경할 수 있습니다.");
        this.planType = target;
    }

    public void reserveVoice(long durationMs) {
        if (durationMs <= 0) throw new IllegalArgumentException("durationMs must be positive");
        if (durationMs > getVoiceRemainingMs())
            throw new BusinessException(ErrorCode.LIMIT_REACHED, "방의 남은 음성 시간을 초과했습니다.");
        voiceReservedMs += durationMs;
    }

    public void settleVoice(long durationMs, boolean success) {
        if (durationMs <= 0 || durationMs > voiceReservedMs) throw new IllegalStateException("Invalid voice reservation");
        voiceReservedMs -= durationMs;
        if (success) voiceUsedMs += durationMs;
    }
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
    @Column(length=500) private String encryptedInviteToken;
    @Column(length=64,unique=true) private String retainedInviteHash;
    public String getEncryptedInviteToken(){return encryptedInviteToken;}
    public void retainInviteToken(String encrypted,String hash){
        if(encryptedInviteToken!=null)throw new IllegalStateException("Invite link already retained");
        encryptedInviteToken=encrypted;retainedInviteHash=hash;
    }

    /** 생성 직후 응답용 원문. 별도 encryptedInviteToken 필드에 암호화해 영구 보관한다. */
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

    /** 방장의 마감 변경 이력 표시. 변경 횟수 제한에 사용하지 않는다. */
    @Column(nullable = false)
    private boolean deadlineChangedByHost;

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
        initializeInviteToken();
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

    public void rename(String title) {
        this.title = title;
    }

    public void changeMeetingDate(LocalDate meetingDate) {
        this.meetingDate = meetingDate;
    }

    /** 방장 마감 변경: 엮기 전까지 횟수 제한 없이 미래 시각으로 변경. */
    public void changeDeadline(LocalDateTime newDeadline, LocalDateTime now) {
        if (status == RoomStatus.WEAVING) throw new BusinessException(ErrorCode.ROOM_WEAVING, "엮는 중에는 마감을 바꿀 수 없어요.");
        if (status == RoomStatus.GROWING) throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "이미 마감된 방이에요.");
        if (!newDeadline.isAfter(now)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "마감 시각은 지금보다 뒤여야 해요.");
        this.deadlineAt = newDeadline;
        this.deadlineChangedByHost = true;
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

    /** 수동 다시 엮기·답가 재엮기 플랜별 한도를 사용한다. */
    public void useRegeneration() {
        if (status != RoomStatus.WEAVING && status != RoomStatus.GROWING) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "마감 후에만 다시 엮을 수 있어요.");
        }
        if (regenerationCount >= planType.regenerations()) {
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

    /** 방 생성 시 한 번만 초대 토큰을 발급한다. */
    private String initializeInviteToken() {
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
