/** 한마디 기록을 저장하고 중복 제출을 검사합니다. */
package com.dandimate.durooduroo.memory.repository;
import com.dandimate.durooduroo.memory.domain.MemoryEntry; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryEntryRepository extends JpaRepository<MemoryEntry,String>{boolean existsByPhotoIdAndMemberId(String p,String m); List<MemoryEntry> findAllByRoomId(String r); java.util.Optional<MemoryEntry> findByPhotoIdAndMemberId(String p,String m); java.util.Optional<MemoryEntry> findFirstByPhotoIdAndStatusOrderByCreatedAtAsc(String p, com.dandimate.durooduroo.memory.domain.EntryStatus s);}
