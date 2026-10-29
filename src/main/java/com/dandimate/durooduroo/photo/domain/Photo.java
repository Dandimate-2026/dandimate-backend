/** 사진 파일의 메타데이터와 업로더를 저장하는 도메인입니다. */
package com.dandimate.durooduroo.photo.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "photos")
public class Photo {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false) private String roomId;
    @Column(nullable = false) private String memberId;
    @Column(nullable = false) private String fileUrl;
    @Column(nullable = false, length = 64) private String fileHash;
    @Column(nullable = false) private LocalDateTime createdAt;
    protected Photo() {}
    private Photo(String roomId, String memberId, String fileUrl, String fileHash) {
        this.id = UUID.randomUUID().toString(); this.roomId = roomId; this.memberId = memberId;
        this.fileUrl = fileUrl; this.fileHash = fileHash; this.createdAt = LocalDateTime.now();
    }
    public static Photo create(String roomId, String memberId, String fileUrl, String fileHash) { return new Photo(roomId, memberId, fileUrl, fileHash); }
    public static Photo createWithId(String id, String roomId, String memberId, String fileUrl, String fileHash) { Photo photo = new Photo(roomId, memberId, fileUrl, fileHash); photo.id = id; return photo; }
    public void complete(String fileUrl, String fileHash) { this.fileUrl = fileUrl; this.fileHash = fileHash; }
    public String getId(){return id;} public String getRoomId(){return roomId;} public String getMemberId(){return memberId;}
    public String getFileUrl(){return fileUrl;} public String getFileHash(){return fileHash;} public LocalDateTime getCreatedAt(){return createdAt;}
}
