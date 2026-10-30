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
    @Column(nullable = false, length = 300) private String storageKey;
    @Column(nullable = false, length = 50) private String contentType;
    @Column(length = 128) private String fileHash;
    @Column(nullable = false) private int displayOrder;
    @Column(nullable = false, length = 20) private String status;
    private LocalDateTime publishedAt;
    @Column(nullable = false) private LocalDateTime createdAt;
    protected Photo() {}
    /** 업로드 URL을 발급하며 UPLOADING 상태로 만든다. storageKey·fileUrl(저장 위치)은 서버가 정한다. */
    public static Photo pending(String id, String roomId, String memberId, String storageKey, String fileUrl, String contentType, int displayOrder) {
        Photo photo = new Photo();
        photo.id = id; photo.roomId = roomId; photo.memberId = memberId; photo.storageKey = storageKey; photo.fileUrl = fileUrl;
        photo.contentType = contentType; photo.displayOrder = displayOrder; photo.status = UPLOADING; photo.createdAt = LocalDateTime.now();
        return photo;
    }
    /** 저장소 검증을 통과하면 READY. */
    public void complete(String fileHash) { this.fileHash = fileHash; this.status = READY; }
    public void publish() { if (publishedAt == null) publishedAt = LocalDateTime.now(); }
    public boolean isReady() { return READY.equals(status); }
    public boolean isPublished() { return publishedAt != null; }
    public String getId(){return id;} public String getRoomId(){return roomId;} public String getMemberId(){return memberId;}
    public String getFileUrl(){return fileUrl;} public String getStorageKey(){return storageKey;} public String getContentType(){return contentType;} public String getFileHash(){return fileHash;} public int getDisplayOrder(){return displayOrder;}
    public String getStatus(){return status;} public LocalDateTime getPublishedAt(){return publishedAt;} public LocalDateTime getCreatedAt(){return createdAt;}
}
