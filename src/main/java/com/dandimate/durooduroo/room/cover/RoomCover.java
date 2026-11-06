package com.dandimate.durooduroo.room.cover;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="room_covers")
public class RoomCover {
    @Id @Column(length=36) private String id;
    @Column(nullable=false,length=36) private String userId;
    @Column(nullable=false,length=1000) private String location;
    @Column(nullable=false,length=50) private String contentType;
    @Column(nullable=false) private LocalDateTime createdAt;
    protected RoomCover() {}
    public RoomCover(String id,String userId,String location,String contentType) {
        this.id=id;this.userId=userId;this.location=location;this.contentType=contentType;this.createdAt=LocalDateTime.now();
    }
    public String getId(){return id;}
    public String getUserId(){return userId;}
    public String getLocation(){return location;}
    public String getContentType(){return contentType;}
}
