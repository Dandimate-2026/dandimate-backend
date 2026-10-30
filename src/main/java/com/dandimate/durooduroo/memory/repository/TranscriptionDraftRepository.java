/** 받아쓰기 초안 저장소입니다. */
package com.dandimate.durooduroo.memory.repository;
import com.dandimate.durooduroo.memory.domain.TranscriptionDraft; import java.time.LocalDateTime; import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface TranscriptionDraftRepository extends JpaRepository<TranscriptionDraft,String>{ List<TranscriptionDraft> findAllByExpiresAtBeforeAndConsumedAtIsNull(LocalDateTime before); }
