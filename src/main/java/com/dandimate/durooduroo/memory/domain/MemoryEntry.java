/** 사진에 대한 참여자의 한마디 또는 패스 기록입니다. */
package com.dandimate.durooduroo.memory.domain;

import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="memory_entries") public class MemoryEntry {
    @Id private String id; @Column(nullable=false) private String roomId; @Column(nullable=false) private String photoId; @Column(nullable=false) private String memberId; @Column(length=3000) private String content; @Column(nullable=false) @Enumerated(EnumType.STRING) private EntryStatus status; @Column(nullable=false) private LocalDateTime createdAt;
    protected MemoryEntry(){} private MemoryEntry(String roomId,String photoId,String memberId,String content,EntryStatus status){this.id=UUID.randomUUID().toString();this.roomId=roomId;this.photoId=photoId;this.memberId=memberId;this.content=content;this.status=status;this.createdAt=LocalDateTime.now();}
    public static MemoryEntry create(String r,String p,String m,String c,EntryStatus s){return new MemoryEntry(r,p,m,c,s);} public void updateContent(String value){this.content=value;this.status=EntryStatus.ANSWERED;} public String getId(){return id;} public String getPhotoId(){return photoId;} public String getContent(){return content;} public EntryStatus getStatus(){return status;} public String getMemberId(){return memberId;} public String getRoomId(){return roomId;}
}
