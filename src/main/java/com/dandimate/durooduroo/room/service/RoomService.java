/** 방 생성 규칙과 초대 코드 중복 검사를 처리한다. */
package com.dandimate.durooduroo.room.service;

import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.dto.CreateRoomRequest;
import com.dandimate.durooduroo.room.dto.CreateRoomResponse;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 6;

    private final RoomRepository roomRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public CreateRoomResponse createRoom(CreateRoomRequest request) {
        String title = request.title().trim();
        String inviteCode = generateUniqueInviteCode();
        Room room = roomRepository.save(Room.create(title, inviteCode));
        return CreateRoomResponse.from(room);
    }

    private String generateUniqueInviteCode() {
        String inviteCode;
        do {
            inviteCode = randomInviteCode();
        } while (roomRepository.existsByInviteCode(inviteCode));
        return inviteCode;
    }

    private String randomInviteCode() {
        StringBuilder code = new StringBuilder(INVITE_CODE_LENGTH);
        for (int index = 0; index < INVITE_CODE_LENGTH; index++) {
            int characterIndex = secureRandom.nextInt(INVITE_CODE_CHARACTERS.length());
            code.append(INVITE_CODE_CHARACTERS.charAt(characterIndex));
        }
        return code.toString();
    }
}
