/** 되묻기 저장소입니다. */
package com.dandimate.durooduroo.askback.repository;
import com.dandimate.durooduroo.askback.domain.Askback; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AskbackRepository extends JpaRepository<Askback,String>{List<Askback> findAllByRoomIdAndTargetMemberId(String roomId,String targetMemberId); List<Askback> findAllByRoomIdAndStatus(String roomId,String status);}
