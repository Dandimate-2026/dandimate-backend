package com.dandimate.durooduroo.reviewerdemo.repository;

import com.dandimate.durooduroo.reviewerdemo.domain.ReviewerDemoSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewerDemoSessionRepository extends JpaRepository<ReviewerDemoSession, String> {
    Optional<ReviewerDemoSession> findByUserId(String userId);
    @org.springframework.data.jpa.repository.Query("select count(s)>0 from ReviewerDemoSession s where s.hostRoomId=:roomId or s.memberRoomId=:roomId or s.completedRoomId=:roomId")
    boolean existsByDemoRoomId(@org.springframework.data.repository.query.Param("roomId") String roomId);
}
