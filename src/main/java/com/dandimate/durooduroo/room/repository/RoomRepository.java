/** 방 데이터를 DB에 저장하고 조회한다. */
package com.dandimate.durooduroo.room.repository;

import com.dandimate.durooduroo.room.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, String> {

    boolean existsByInviteCode(String inviteCode);

    java.util.Optional<Room> findByInviteCode(String inviteCode);
}
