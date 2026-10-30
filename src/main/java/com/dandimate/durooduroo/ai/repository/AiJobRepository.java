/** AI 작업 상태를 저장하는 저장소입니다. */
package com.dandimate.durooduroo.ai.repository;

import com.dandimate.durooduroo.ai.domain.AiJob;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AiJobRepository extends JpaRepository<AiJob, String> {

    Optional<AiJob> findFirstByRoomIdAndTypeAndStatusInOrderByCreatedAtDesc(String roomId, String type, Collection<String> statuses);

    /**
     * 실행할 작업을 선점한다. lock.timeout=-2는 Hibernate의 SKIP LOCKED로, MySQL 8에서는
     * SELECT ... FOR UPDATE SKIP LOCKED가 되어 Worker 여러 개가 같은 작업을 잡지 않는다.
     * (H2처럼 SKIP LOCKED가 없는 DB에서는 일반 FOR UPDATE로 동작한다.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select j from AiJob j where j.status = 'QUEUED' and j.type in :types and j.nextRunAt <= :now order by j.createdAt")
    List<AiJob> findRunnable(@Param("types") Collection<String> types, @Param("now") LocalDateTime now, Pageable pageable);

    List<AiJob> findAllByStatusAndUpdatedAtBefore(String status, LocalDateTime updatedBefore);

    Optional<AiJob> findFirstByTypeAndTargetIdAndStatus(String type, String targetId, String status);
}
