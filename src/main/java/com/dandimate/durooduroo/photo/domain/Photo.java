/**
 * 사진 파일의 메타데이터와 업로더를 저장하는 도메인입니다.
 * 업로더가 첫 한마디를 확정하거나 "나중에"를 누르면 publishedAt이 찍히고 돌려보기에 노출된다.
 */
package com.dandimate.durooduroo.photo.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "photos")
public class Photo {
    public static final String UPLOADING = "UPLOADING";
    public static final String READY = "READY";

    @Id @Column(length = 36) private String id;
    @Column(nullable = false) private String roomId;
    @Column(nullable = false) private String memberId;
    @Column(nullable = false) private String fileUrl;
    @Column(nullable = false, length = 64) private String fileHash;
    @Column(nullable = false) private int displayOrder;
    @Column(nullable = false, length = 20) private String status;
    private LocalDateTime publishedAt;
    @Column(nullable = false) private LocalDateTime createdAt;
    protected Photo() {}
    private Photo(String roomId, String memberId, String fileUrl, String fileHash, int displayOrder, String status) {
        this.id = UUID.randomUUID().toString(); this.roomId = roomId; this.memberId = memberId;
        this.fileUrl = fileUrl; this.fileHash = fileHash; this.displayOrder = displayOrder; this.status = status; this.createdAt = LocalDateTime.now();
    }
    /** 서버에 바로 저장된 사진(레거시 업로드). */
    public static Photo create(String roomId, String memberId, String fileUrl, String fileHash, int displayOrder) { return new Photo(roomId, memberId, fileUrl, fileHash, displayOrder, READY); }
    /** 업로드 URL을 발급하며 UPLOADING 상태로 만든다. */
    public static Photo pending(String id, String roomId, String memberId, String fileUrl, String fileHash, int displayOrder) { Photo photo = new Photo(roomId, memberId, fileUrl, fileHash, displayOrder, UPLOADING); photo.id = id; return photo; }
    public void complete(String fileUrl, String fileHash) { this.fileUrl = fileUrl; this.fileHash = fileHash; this.status = READY; }
    public void publish() { if (publishedAt == null) publishedAt = LocalDateTime.now(); }
    public boolean isReady() { return READY.equals(status); }
    public boolean isPublished() { return publishedAt != null; }
    public String getId(){return id;} public String getRoomId(){return roomId;} public String getMemberId(){return memberId;}
    public String getFileUrl(){return fileUrl;} public String getFileHash(){return fileHash;} public int getDisplayOrder(){return displayOrder;}
    public String getStatus(){return status;} public LocalDateTime getPublishedAt(){return publishedAt;} public LocalDateTime getCreatedAt(){return createdAt;}
}
