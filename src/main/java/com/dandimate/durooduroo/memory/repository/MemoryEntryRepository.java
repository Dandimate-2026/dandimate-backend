/** 확정 원문을 저장하고 사진·참여자별로 조회합니다. */
package com.dandimate.durooduroo.memory.repository;
import com.dandimate.durooduroo.memory.domain.EntryKind; import com.dandimate.durooduroo.memory.domain.MemoryEntry; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryEntryRepository extends JpaRepository<MemoryEntry,String>{ List<MemoryEntry> findAllByRoomId(String roomId); List<MemoryEntry> findAllByRoomIdOrderByCreatedAtAsc(String roomId); List<MemoryEntry> findAllByPhotoId(String photoId); boolean existsByPhotoIdAndKind(String photoId,EntryKind kind); long countByPhotoIdAndMemberIdAndKind(String photoId,String memberId,EntryKind kind); }
