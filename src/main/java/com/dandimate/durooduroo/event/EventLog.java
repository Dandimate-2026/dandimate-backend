/** 지표용 이벤트 로그입니다 (명세 3장·6-1장 이벤트 표). 원문·URL은 넣지 않는다. */
package com.dandimate.durooduroo.event;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_logs", indexes = @Index(name = "idx_event_logs_room_name", columnList = "roomId,name"))
public class EventLog {
    @Id private String id;
    @Column(nullable = false) private String roomId;
    private String memberId;
    @Column(nullable = false, length = 50) private String name;
    @Column(length = 1000) private String properties;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected EventLog() {}

    public static EventLog of(String roomId, String memberId, String name, String properties) {
        EventLog log = new EventLog();
        log.id = UUID.randomUUID().toString(); log.roomId = roomId; log.memberId = memberId; log.name = name; log.properties = properties;
        log.createdAt = LocalDateTime.now();
        return log;
    }

    public String getRoomId(){return roomId;} public String getMemberId(){return memberId;} public String getName(){return name;} public String getProperties(){return properties;} public LocalDateTime getCreatedAt(){return createdAt;}
}
