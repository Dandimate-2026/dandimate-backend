package com.dandimate.durooduroo.embedding.repository;
import com.dandimate.durooduroo.embedding.domain.MemoryEmbedding;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface MemoryEmbeddingRepository extends JpaRepository<MemoryEmbedding,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from MemoryEmbedding e where e.status in ('PENDING','PROCESSING') and e.nextAttemptAt <= :now order by e.nextAttemptAt")
    List<MemoryEmbedding> claimable(@Param("now") LocalDateTime now, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from MemoryEmbedding e where e.entryId=:id")
    Optional<MemoryEmbedding> locked(@Param("id") String id);
}
