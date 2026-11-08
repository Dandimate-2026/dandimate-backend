package com.dandimate.durooduroo.chat.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="chat_messages", indexes=@Index(name="idx_chat_messages_conversation", columnList="userId,conversationId,createdAt"))
public class ChatMessage {
 @Column(nullable=false,length=36) private String demoScope="REAL";
 @Lob @Column(columnDefinition="TEXT") private String sourceRoomIds;
 public void setSourceRoomIds(String ids){sourceRoomIds=ids;}
 public void setDemoScope(String scope){this.demoScope=scope;}
 public String getDemoScope(){return demoScope;}
    @Id private String id; @Column(nullable=false) private String userId; @Column(nullable=false,length=64) private String conversationId; @Column(nullable=false,length=12) private String role; @Column(nullable=false,length=1_000_000) private String content; @Column(nullable=false) private LocalDateTime createdAt;
    protected ChatMessage() {}
    public static ChatMessage of(String userId,String conversationId,String role,String content){ ChatMessage m=new ChatMessage();m.id=UUID.randomUUID().toString();m.userId=userId;m.conversationId=conversationId;m.role=role;m.content=content;m.createdAt=LocalDateTime.now();return m; }
    public String getId(){return id;} public String getConversationId(){return conversationId;} public String getRole(){return role;} public String getContent(){return content;} public LocalDateTime getCreatedAt(){return createdAt;}
}
