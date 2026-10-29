/** 상태별 쓰기 허용 검사와 첫 한마디 확정에 따른 상태 전이를 처리한다. */
package com.dandimate.durooduroo.room.service;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomAction;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomStateService {

    private final RoomRepository roomRepository;

    public RoomStateService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public Room require(String roomId, RoomAction action) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "존재하지 않는 방입니다."));
        room.requireAllows(action);
        return room;
    }

    /** 사진을 올린 사람의 첫 한마디가 확정되면 방을 돌려보기 상태로 바꾼다. */
    @Transactional
    public void onFirstEntryConfirmed(String roomId) {
        roomRepository.findByIdForUpdate(roomId).ifPresent(Room::startCirculating);
    }
}
