/**
 * 참여자 한 명에게 사진의 빈 곳을 묻는 되묻기입니다.
 * PENDING → ANSWERED(답변) / FORGOT(기억 안 나요) / EXPIRED(엮기 시작) / CANCELLED(사진 삭제). 종료된 되묻기에 답하면 409 ASKBACK_CLOSED.
 * "나중에"는 PENDING을 유지한 채 postponedAt만 기록한다.
 */
package com.dandimate.durooduroo.askback.domain;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "askbacks", indexes = @Index(name = "idx_askbacks_target_status", columnList = "targetMemberId,status"))
public class Askback {
    public static final String PENDING = "PENDING";

    @Id private String id;
    @Column(nullable = false) private String roomId;
    @Column(nullable = false) private String photoId;
    @Column(nullable = false) private String targetMemberId;
    @Column(nullable = false, length = 30) private String gapType;
    @Column(nullable = false, length = 200) private String question;
    @Column(length = 2000) private String evidenceEntryIds;
    @Column(nullable = false, length = 20) private String status;
    private String answerEntryId;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private LocalDateTime postponedAt;
    private LocalDateTime resolvedAt;

    protected Askback() {}

    private Askback(String roomId, String photoId, String targetMemberId, String gapType, String question,
                    List<String> evidenceEntryIds, LocalDateTime expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.roomId = roomId; this.photoId = photoId; this.targetMemberId = targetMemberId;
        this.gapType = gapType; this.question = question; this.evidenceEntryIds = String.join(",", evidenceEntryIds);
        this.status = PENDING; this.createdAt = LocalDateTime.now(); this.expiresAt = expiresAt;
    }

    public static Askback create(String roomId, String photoId, String targetMemberId, String gapType, String question,
                                 List<String> evidenceEntryIds, LocalDateTime expiresAt) {
        return new Askback(roomId, photoId, targetMemberId, gapType, question, evidenceEntryIds, expiresAt);
    }

    public void answer(String entryId) { requirePending(); status = "ANSWERED"; answerEntryId = entryId; resolvedAt = LocalDateTime.now(); }
    public void forgot() { requirePending(); status = "FORGOT"; resolvedAt = LocalDateTime.now(); }
    public void postpone() { requirePending(); postponedAt = LocalDateTime.now(); }
    public void cancel() { if (PENDING.equals(status)) { status = "CANCELLED"; resolvedAt = LocalDateTime.now(); } }
    public void expire() { if (PENDING.equals(status)) { status = "EXPIRED"; resolvedAt = LocalDateTime.now(); } }

    private void requirePending() {
        if (!PENDING.equals(status)) throw new BusinessException(ErrorCode.ASKBACK_CLOSED, "이미 끝난 질문이에요.");
    }

    public List<String> getEvidenceEntryIds() {
        return evidenceEntryIds == null || evidenceEntryIds.isBlank() ? List.of() : Arrays.asList(evidenceEntryIds.split(","));
    }

    public String getId(){return id;} public String getRoomId(){return roomId;} public String getPhotoId(){return photoId;} public String getTargetMemberId(){return targetMemberId;}
    public String getGapType(){return gapType;} public String getQuestion(){return question;} public String getStatus(){return status;} public String getAnswerEntryId(){return answerEntryId;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getExpiresAt(){return expiresAt;} public LocalDateTime getPostponedAt(){return postponedAt;} public LocalDateTime getResolvedAt(){return resolvedAt;}
}
