/** 방 데이터를 DB에 저장하고 조회한다. */
package com.dandimate.durooduroo.room.repository;

import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.room.domain.Room;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, String> {

    Optional<Room> findByInviteTokenHash(String inviteTokenHash);

    /** 초대 토큰 원문으로 방을 찾는다. 재발급 전 토큰은 해시가 달라 찾지 못한다. */
    default Optional<Room> findByInviteToken(String rawInviteToken) {
        return findByInviteTokenHash(Tokens.hash(rawInviteToken));
    }

    /** 마감·다시 엮기처럼 상태를 바꾸는 요청이 동시에 들어와도 한 번만 처리되도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") String id);
}
