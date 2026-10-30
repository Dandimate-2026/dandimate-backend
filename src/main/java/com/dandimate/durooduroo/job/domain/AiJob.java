/**
 * AI 작업(문단 생성·되묻기 분석)의 상태입니다. Worker가 QUEUED → RUNNING으로 선점해 실행하고,
 * 실패하면 attempts를 보고 다시 QUEUED(nextRunAt 이후 실행)로 돌리거나 FAILED로 끝낸다.
 */
package com.dandimate.durooduroo.job.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity @Table(name="ai_jobs", indexes=@Index(name="idx_ai_jobs_runnable", columnList="status,nextRunAt"))
public class AiJob {
    public static final String WEAVE_SCROLL="WEAVE_SCROLL"; public static final String ASKBACK_ANALYZE="ASKBACK_ANALYZE"; public static final String REPLY_REWEAVE="REPLY_REWEAVE"; public static final String MEDIA_DELETE="MEDIA_DELETE";
    public static final List<String> ACTIVE_STATUSES=List.of("QUEUED","RUNNING");
    @Id private String id; @Column(nullable=false) private String roomId; @Column(nullable=false) private String type; /** 작업 대상(ASKBACK_ANALYZE는 사진 ID, REPLY_REWEAVE는 답가 ID 또는 복구할 문단 ID). */ private String targetId; /** 작업 부가 정보(문단 복구 사유 등). */ private String detail; @Column(nullable=false) private String status; private String errorCode;
    @Column(nullable=false) private int attempts; @Column(nullable=false) private LocalDateTime nextRunAt; @Column(nullable=false) private LocalDateTime createdAt; @Column(nullable=false) private LocalDateTime updatedAt;
    protected AiJob() {}
    private AiJob(String roomId,String type) { id=UUID.randomUUID().toString(); this.roomId=roomId; this.type=type; status="QUEUED"; createdAt=LocalDateTime.now(); updatedAt=createdAt; nextRunAt=createdAt; }
    public static AiJob queued(String roomId,String type){return new AiJob(roomId,type);} public static AiJob queued(String roomId,String type,String targetId){AiJob job=new AiJob(roomId,type);job.targetId=targetId;return job;} public static AiJob queued(String roomId,String type,String targetId,String detail){AiJob job=queued(roomId,type,targetId);job.detail=detail;return job;}
    /** Worker가 실행을 시작한다. */
    public void claim(){status="RUNNING";attempts++;updatedAt=LocalDateTime.now();}
    public void succeed(){status="SUCCEEDED";errorCode=null;updatedAt=LocalDateTime.now();}
    /** 재시도: delay 뒤에 다시 실행되도록 QUEUED로 돌린다. */
    public void retryAfter(Duration delay,String errorCode){status="QUEUED";this.errorCode=errorCode;updatedAt=LocalDateTime.now();nextRunAt=updatedAt.plus(delay);}
    public void fail(String errorCode){status="FAILED";this.errorCode=errorCode;updatedAt=LocalDateTime.now();}
    /** Worker가 죽어 RUNNING으로 남은 작업을 다시 실행 대기로 돌린다. */
    public void requeue(){status="QUEUED";updatedAt=LocalDateTime.now();nextRunAt=updatedAt;}
    public String getId(){return id;} public String getRoomId(){return roomId;} public String getType(){return type;} public String getTargetId(){return targetId;} public String getDetail(){return detail;} public String getStatus(){return status;} public String getErrorCode(){return errorCode;} public int getAttempts(){return attempts;} public LocalDateTime getNextRunAt(){return nextRunAt;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
