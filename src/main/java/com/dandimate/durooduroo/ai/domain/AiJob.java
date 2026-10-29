/** MVP에서 문단 생성 작업의 상태를 저장합니다. */
package com.dandimate.durooduroo.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="ai_jobs")
public class AiJob {
    @Id private String id; @Column(nullable=false) private String roomId; @Column(nullable=false) private String status; @Column(nullable=false) private LocalDateTime createdAt; @Column(nullable=false) private LocalDateTime updatedAt;
    protected AiJob() {}
    private AiJob(String roomId) { id=UUID.randomUUID().toString(); this.roomId=roomId; status="QUEUED"; createdAt=LocalDateTime.now(); updatedAt=createdAt; }
    public static AiJob queued(String roomId){return new AiJob(roomId);} public void succeed(){status="SUCCEEDED";updatedAt=LocalDateTime.now();} public void fail(){status="FAILED";updatedAt=LocalDateTime.now();}
    public String getId(){return id;} public String getRoomId(){return roomId;} public String getStatus(){return status;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
