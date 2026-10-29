/** 생성된 두루마리 문단을 저장하는 도메인입니다. */
package com.dandimate.durooduroo.scroll.domain;
import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="scrolls") public class Scroll { @Id private String id; @Column(nullable=false) private String roomId; @Lob @Column(nullable=false) private String body; @Column(nullable=false) private LocalDateTime createdAt; protected Scroll(){} private Scroll(String r,String b){id=UUID.randomUUID().toString();roomId=r;body=b;createdAt=LocalDateTime.now();} public static Scroll create(String r,String b){return new Scroll(r,b);} public String getId(){return id;} public String getRoomId(){return roomId;} public String getBody(){return body;} public LocalDateTime getCreatedAt(){return createdAt;} }
