/** 방 데이터를 DB에 저장하고 조회한다. */
package com.dandimate.durooduroo.room.repository;

import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.room.domain.Room;
import jakarta.persistence.LockModeType;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, String> {

    @Query("select r from Room r where r.inviteTokenHash=:hash or r.retainedInviteHash=:hash")
    Optional<Room> findByInviteTokenHash(@Param("hash") String inviteTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.inviteTokenHash = :hash or r.retainedInviteHash = :hash")
    Optional<Room> findByInviteTokenHashForUpdate(@Param("hash") String hash);

    List<Room> findAllByStatusInAndDeadlineAtLessThanEqual(Collection<RoomStatus> statuses, LocalDateTime now);

    /** 초대 토큰 원문으로 방을 찾는다. 기존 방의 이전 링크도 계속 유효하다. */
    default Optional<Room> findByInviteToken(String rawInviteToken) {
        return findByInviteTokenHash(Tokens.hash(rawInviteToken));
    }

    /** 마감·다시 엮기처럼 상태를 바꾸는 요청이 동시에 들어와도 한 번만 처리되도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") String id);
}
