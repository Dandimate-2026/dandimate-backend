/** 방 생성 규칙을 처리한다. */
package com.dandimate.durooduroo.room.service;

import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.dto.CreateRoomRequest;
import com.dandimate.durooduroo.room.dto.CreateRoomResponse;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public CreateRoomResponse createRoom(CreateRoomRequest request) {
        String title = request.title().trim();
        Room room = roomRepository.save(Room.create(title));
        return CreateRoomResponse.from(room);
    }

}
