package com.dandimate.durooduroo.reviewerdemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** 한 로그인 사용자에게 연결한 심사용 샘플 방 묶음이다. */
@Entity
@Table(name = "reviewer_demo_sessions")
public class ReviewerDemoSession {
    @Id @Column(length = 36) private String id;
    @Column(name = "user_id", nullable = false, unique = true, length = 36) private String userId;
    @Column(name = "host_room_id", nullable = false, length = 36) private String hostRoomId;
    @Column(name = "member_room_id", nullable = false, length = 36) private String memberRoomId;
    @Column(name = "completed_room_id", nullable = false, length = 36) private String completedRoomId;
    @Column(name = "seed_version", nullable = false) private int seedVersion;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected ReviewerDemoSession() { }

    private ReviewerDemoSession(String userId, String hostRoomId, String memberRoomId, String completedRoomId, int seedVersion) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.hostRoomId = hostRoomId;
        this.memberRoomId = memberRoomId;
        this.completedRoomId = completedRoomId;
        this.seedVersion = seedVersion;
        this.createdAt = LocalDateTime.now();
    }

    public static ReviewerDemoSession create(String userId, String hostRoomId, String memberRoomId, String completedRoomId, int seedVersion) {
        return new ReviewerDemoSession(userId, hostRoomId, memberRoomId, completedRoomId, seedVersion);
    }

    public String getId() { return id; }
    public String getHostRoomId() { return hostRoomId; }
    public String getMemberRoomId() { return memberRoomId; }
    public String getCompletedRoomId() { return completedRoomId; }
    public int getSeedVersion() { return seedVersion; }
}
