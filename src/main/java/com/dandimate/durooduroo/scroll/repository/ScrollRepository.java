/** 두루마리 결과를 조회하는 저장소입니다. */
package com.dandimate.durooduroo.scroll.repository;
import com.dandimate.durooduroo.scroll.domain.Scroll; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ScrollRepository extends JpaRepository<Scroll,String>{Optional<Scroll> findTopByRoomIdOrderByCreatedAtDesc(String roomId);}
