/** 사진에 대한 참여자의 확정 원문(첫 한마디·덧붙임·되묻기 답)입니다. 패스는 PhotoPass로 따로 저장한다. */
package com.dandimate.durooduroo.memory.domain;

import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="memory_entries", indexes=@Index(name="idx_entries_photo", columnList="photoId")) public class MemoryEntry {
    @Id private String id; @Column(nullable=false) private String roomId; @Column(nullable=false) private String photoId; @Column(nullable=false) private String memberId;
    @Column(nullable=false,length=20) @Enumerated(EnumType.STRING) private EntryKind kind; @Column(nullable=false,length=10) private String inputMode; private String transcriptionId;
    @Column(nullable=false,length=3000) private String transcript; @Column(nullable=false) private LocalDateTime createdAt; @Column(nullable=false) private LocalDateTime confirmedAt;
    protected MemoryEntry(){}
    private MemoryEntry(String roomId,String photoId,String memberId,EntryKind kind,String inputMode,String transcriptionId,String transcript){this.id=UUID.randomUUID().toString();this.roomId=roomId;this.photoId=photoId;this.memberId=memberId;this.kind=kind;this.inputMode=inputMode;this.transcriptionId=transcriptionId;this.transcript=transcript;this.createdAt=LocalDateTime.now();this.confirmedAt=createdAt;}
    public static MemoryEntry confirm(String roomId,String photoId,String memberId,EntryKind kind,String inputMode,String transcriptionId,String transcript){return new MemoryEntry(roomId,photoId,memberId,kind,inputMode,transcriptionId,transcript);}
    public void edit(String transcript){this.transcript=transcript;this.confirmedAt=LocalDateTime.now();}
    public String getId(){return id;} public String getRoomId(){return roomId;} public String getPhotoId(){return photoId;} public String getMemberId(){return memberId;} public EntryKind getKind(){return kind;} public String getInputMode(){return inputMode;} public String getTranscriptionId(){return transcriptionId;} public String getTranscript(){return transcript;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getConfirmedAt(){return confirmedAt;}
}
