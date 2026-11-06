package com.dandimate.durooduroo.chat.quota;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="chat_usage")
public class ChatUsage {
 @Id @Column(length=64) String id;
 @Column(nullable=false,length=36) String userId;
 @Column(nullable=false,length=64) String requestHash;
 @Column(nullable=false,length=16) String status;
 @Column(length=36) String attemptId;
 boolean free;
 LocalDate usageDate;
 Instant expiresAt;
 @Lob @Column(columnDefinition="LONGTEXT") String response;
 protected ChatUsage() {}
}
