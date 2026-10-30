/** 사진 패스 저장소입니다. */
package com.dandimate.durooduroo.memory.repository;
import com.dandimate.durooduroo.memory.domain.PhotoPass; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface PhotoPassRepository extends JpaRepository<PhotoPass,String>{ Optional<PhotoPass> findByPhotoIdAndMemberId(String photoId,String memberId); List<PhotoPass> findAllByRoomIdAndMemberId(String roomId,String memberId); List<PhotoPass> findAllByPhotoId(String photoId); }
