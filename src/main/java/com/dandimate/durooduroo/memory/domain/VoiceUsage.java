package com.dandimate.durooduroo.memory.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 원문 삭제·초안 만료와 별개로 유지하는 STT 사용량 및 멱등 요청 기록. */
@Entity
@Table(name = "voice_usage", indexes = @Index(name = "idx_voice_usage_room", columnList = "roomId"))
public class VoiceUsage {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 36) private String roomId;
    @Column(nullable = false, length = 36) private String memberId;
    @Column(nullable = false, length = 64) private String requestHash;
    @Column(nullable = false) private long durationMs;
    @Column(nullable = false, length = 12) private String status;
    @Column(length = 36) private String draftId;
    @Column(nullable = false) private LocalDateTime updatedAt;

    @Column(length = 300) private String languages;

    protected VoiceUsage() {}
    public static VoiceUsage reserve(String id, String roomId, String memberId, String requestHash, long durationMs) {
        VoiceUsage usage = new VoiceUsage();
        usage.id = id; usage.roomId = roomId; usage.memberId = memberId;
        usage.requestHash = requestHash; usage.durationMs = durationMs; usage.start();
        return usage;
    }
    public void start() { status = "RESERVED"; updatedAt = LocalDateTime.now(); }
    public void finish(String draftId, java.util.List<String> languages) { this.languages = String.join(",", languages); this.draftId = draftId; status = "SUCCEEDED"; updatedAt = LocalDateTime.now(); }
    public void expire() { status = "EXPIRED"; updatedAt = LocalDateTime.now(); }
    public void fail() { status = "FAILED"; updatedAt = LocalDateTime.now(); }
    public java.util.List<String> getLanguages() { return languages == null || languages.isEmpty() ? java.util.List.of() : java.util.List.of(languages.split(",")); }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getId() { return id; }
    public String getRoomId() { return roomId; }
    public String getRequestHash() { return requestHash; }
    public long getDurationMs() { return durationMs; }
    public String getStatus() { return status; }
    public String getDraftId() { return draftId; }
}
