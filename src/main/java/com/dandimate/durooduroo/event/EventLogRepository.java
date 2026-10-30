/** 이벤트 로그 저장소입니다. */
package com.dandimate.durooduroo.event;
import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface EventLogRepository extends JpaRepository<EventLog,String>{ boolean existsByRoomIdAndName(String roomId,String name); List<EventLog> findAllByRoomIdAndName(String roomId,String name); }
