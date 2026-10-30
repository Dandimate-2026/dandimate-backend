/**
 * 받아쓰기 초안입니다. 확정 전에는 다른 참여자에게 공개하지 않는다.
 * contextType(PHOTO/ASKBACK/PARAGRAPH)·contextId로 어디에서 만든 초안인지 묶고, 24시간 뒤 만료한다.
 * 원문을 확정하면 consumedAt을 기록해 다시 쓸 수 없게 한다.
 */
package com.dandimate.durooduroo.memory.domain;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transcription_drafts")
public class TranscriptionDraft {
    public static final Duration TTL = Duration.ofHours(24);

    @Id private String id;
    @Column(nullable = false) private String roomId;
    @Column(nullable = false) private String memberId;
    @Column(nullable = false, length = 20) private String contextType;
    @Column(nullable = false) private String contextId;
    @Column(nullable = false, length = 20) private String kind;
    @Column(nullable = false) private String audioAssetId;
    @Column(nullable = false, length = 300) private String audioLocation;
    @Column(nullable = false, length = 3000) private String transcript;
    private Long durationMs;
    private String model;
    private String openaiRequestId;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;

    protected TranscriptionDraft() {}

    public static TranscriptionDraft create(String id, String roomId, String memberId, String contextType, String contextId, String kind,
                                            String audioAssetId, String audioLocation, String transcript, Long durationMs, String model, String openaiRequestId) {
        TranscriptionDraft draft = new TranscriptionDraft();
        draft.id = id; draft.roomId = roomId; draft.memberId = memberId; draft.contextType = contextType; draft.contextId = contextId; draft.kind = kind;
        draft.audioAssetId = audioAssetId; draft.audioLocation = audioLocation; draft.transcript = transcript; draft.durationMs = durationMs;
        draft.model = model; draft.openaiRequestId = openaiRequestId; draft.createdAt = LocalDateTime.now(); draft.expiresAt = draft.createdAt.plus(TTL);
        return draft;
    }

    /** 본인 것인지, 같은 리소스용인지, 아직 쓰지 않았고 만료되지 않았는지 확인한 뒤 소비한다. */
    public void consume(String memberId, String contextType, String contextId) {
        if (!this.memberId.equals(memberId) || !this.contextType.equals(contextType) || !this.contextId.equals(contextId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이 원문에 쓸 수 없는 받아쓰기 초안이에요.");
        }
        if (consumedAt != null || LocalDateTime.now().isAfter(expiresAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미 사용했거나 만료된 받아쓰기 초안이에요. 다시 녹음해 주세요.");
        }
        consumedAt = LocalDateTime.now();
    }

    public String getId(){return id;} public String getRoomId(){return roomId;} public String getMemberId(){return memberId;} public String getContextType(){return contextType;}
    public String getContextId(){return contextId;} public String getKind(){return kind;} public String getAudioAssetId(){return audioAssetId;} public String getAudioLocation(){return audioLocation;}
    public String getTranscript(){return transcript;} public Long getDurationMs(){return durationMs;} public LocalDateTime getExpiresAt(){return expiresAt;} public LocalDateTime getConsumedAt(){return consumedAt;}
}
