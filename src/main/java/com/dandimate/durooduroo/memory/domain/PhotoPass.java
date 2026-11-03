/** 타인 사진에 대한 패스입니다. 참여자·사진당 하나만 존재한다. */
package com.dandimate.durooduroo.memory.domain;

import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="photo_passes", uniqueConstraints=@UniqueConstraint(name="uk_photo_passes_photo_member", columnNames={"photo_id","member_id"})) public class PhotoPass {
    @Id private String id; @Column(nullable=false) private String roomId; @Column(name="photo_id",nullable=false) private String photoId; @Column(name="member_id",nullable=false) private String memberId; @Column(nullable=false) private LocalDateTime createdAt;
    protected PhotoPass(){}
    private PhotoPass(String roomId,String photoId,String memberId){id=UUID.randomUUID().toString();this.roomId=roomId;this.photoId=photoId;this.memberId=memberId;createdAt=LocalDateTime.now();}
    public static PhotoPass of(String roomId,String photoId,String memberId){return new PhotoPass(roomId,photoId,memberId);}
    public String getId(){return id;} public String getRoomId(){return roomId;} public String getPhotoId(){return photoId;} public String getMemberId(){return memberId;} public LocalDateTime getCreatedAt(){return createdAt;}
}
