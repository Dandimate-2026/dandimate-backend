package com.dandimate.durooduroo.reviewerdemo.repository;

import com.dandimate.durooduroo.reviewerdemo.domain.ReviewerDemoSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewerDemoSessionRepository extends JpaRepository<ReviewerDemoSession, String> {
    Optional<ReviewerDemoSession> findByUserId(String userId);
}
