/**
 * 문단에 남기는 답가입니다 (명세 9장). PENDING → APPLIED(본문 반영) / REACTION(반응으로 저장) / REJECTED(UNSAFE), 작성자가 지우면 DELETED.
 * classification은 분류 결과(NEW_FACT/CORRECTION/REACTION_ONLY/UNSAFE)로, 문단을 다시 만들 때 같은 방식으로 재적용한다.
 */
package com.dandimate.durooduroo.scroll.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "replies", indexes = @Index(name = "idx_replies_paragraph", columnList = "paragraphId"))
public class Reply {
    public static final String PENDING = "PENDING";
    public static final String APPLIED = "APPLIED";
    public static final String REACTION = "REACTION";
    public static final String REJECTED = "REJECTED";
    public static final String DELETED = "DELETED";

    @Id private String id;
    @Column(nullable = false) private String roomId;
    @Column(nullable = false) private String paragraphId;
    @Column(nullable = false) private String photoId;
    @Column(nullable = false) private String memberId;
    @Column(nullable = false, length = 10) private String inputMode;
    private String transcriptionId;
    @Column(length = 300) private String audioLocation;
    @Column(nullable = false, length = 1000) private String transcript;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 20) private String classification;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    protected Reply() {}

    public static Reply create(String roomId, String paragraphId, String photoId, String memberId, String inputMode,
                               String transcriptionId, String audioLocation, String transcript) {
        Reply reply = new Reply();
        reply.id = UUID.randomUUID().toString(); reply.roomId = roomId; reply.paragraphId = paragraphId; reply.photoId = photoId; reply.memberId = memberId;
        reply.inputMode = inputMode; reply.transcriptionId = transcriptionId; reply.audioLocation = audioLocation; reply.transcript = transcript;
        reply.status = PENDING; reply.createdAt = LocalDateTime.now();
        return reply;
    }

    public void resolve(String status, String classification) { this.status = status; this.classification = classification; this.resolvedAt = LocalDateTime.now(); }
    public void delete() { this.status = DELETED; this.resolvedAt = LocalDateTime.now(); }

    public String getId(){return id;} public String getRoomId(){return roomId;} public String getParagraphId(){return paragraphId;} public String getPhotoId(){return photoId;}
    public String getMemberId(){return memberId;} public String getInputMode(){return inputMode;} public String getAudioLocation(){return audioLocation;} public String getTranscript(){return transcript;}
    public String getStatus(){return status;} public String getClassification(){return classification;} public LocalDateTime getCreatedAt(){return createdAt;}
}
