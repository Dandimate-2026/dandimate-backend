package com.dandimate.durooduroo.memory.repository;

import com.dandimate.durooduroo.memory.domain.VoiceUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceUsageRepository extends JpaRepository<VoiceUsage, String> {
    java.util.List<VoiceUsage> findAllByStatusAndUpdatedAtBefore(String status, java.time.LocalDateTime cutoff);
}
