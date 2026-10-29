/** 방 생성 API가 반환하는 결과 형식이다. */
package com.dandimate.durooduroo.room.dto;

import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import java.time.LocalDateTime;

public record CreateRoomResponse(
        String roomId,
        String title,
        String inviteCode,
        RoomStatus status,
        LocalDateTime createdAt
) {

    public static CreateRoomResponse from(Room room) {
        return new CreateRoomResponse(
                room.getId(),
                room.getTitle(),
                room.getInviteCode(),
                room.getStatus(),
                room.getCreatedAt()
        );
    }
}
