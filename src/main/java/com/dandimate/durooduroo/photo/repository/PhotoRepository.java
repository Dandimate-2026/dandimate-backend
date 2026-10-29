/** 사진 조회와 중복 검사를 담당하는 저장소입니다. */
package com.dandimate.durooduroo.photo.repository;

import com.dandimate.durooduroo.photo.domain.Photo;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoRepository extends JpaRepository<Photo, String> {
    Optional<Photo> findByRoomIdAndFileHash(String roomId, String fileHash);
    boolean existsByRoomIdAndMemberId(String roomId, String memberId);
    long countByRoomIdAndMemberId(String roomId, String memberId);
    List<Photo> findAllByRoomId(String roomId);
}
