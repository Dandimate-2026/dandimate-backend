/** MVP에서 AI 작업(문단 생성·되묻기 분석)의 상태를 저장합니다. */
package com.dandimate.durooduroo.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity @Table(name="ai_jobs")
public class AiJob {
    public static final String WEAVE_SCROLL="WEAVE_SCROLL"; public static final String ASKBACK_ANALYZE="ASKBACK_ANALYZE";
    public static final List<String> ACTIVE_STATUSES=List.of("QUEUED","RUNNING");
    @Id private String id; @Column(nullable=false) private String roomId; @Column(nullable=false) private String type; @Column(nullable=false) private String status; private String errorCode; @Column(nullable=false) private LocalDateTime createdAt; @Column(nullable=false) private LocalDateTime updatedAt;
    protected AiJob() {}
    private AiJob(String roomId,String type) { id=UUID.randomUUID().toString(); this.roomId=roomId; this.type=type; status="QUEUED"; createdAt=LocalDateTime.now(); updatedAt=createdAt; }
    public static AiJob queued(String roomId,String type){return new AiJob(roomId,type);}
    public void start(){status="RUNNING";updatedAt=LocalDateTime.now();} public void succeed(){status="SUCCEEDED";errorCode=null;updatedAt=LocalDateTime.now();} public void fail(String errorCode){status="FAILED";this.errorCode=errorCode;updatedAt=LocalDateTime.now();}
    public String getId(){return id;} public String getRoomId(){return roomId;} public String getType(){return type;} public String getStatus(){return status;} public String getErrorCode(){return errorCode;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
