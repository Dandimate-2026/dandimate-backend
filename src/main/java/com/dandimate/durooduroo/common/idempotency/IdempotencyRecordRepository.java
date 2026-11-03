/** Idempotency-Key 기록 저장소입니다. */
package com.dandimate.durooduroo.common.idempotency;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    Optional<IdempotencyRecord> findByScopeAndMethodAndPathAndKey(String scope, String method, String path, String key);

    @Transactional
    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :before")
    int deleteCreatedBefore(@Param("before") LocalDateTime before);
}
