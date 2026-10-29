package com.dandimate.durooduroo.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.dto.CreateRoomRequest;
import com.dandimate.durooduroo.room.dto.CreateRoomResponse;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void 방을_생성하면_제목과_초대코드가_반환된다() {
        when(roomRepository.existsByInviteCode(any())).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomResponse response = roomService.createRoom(new CreateRoomRequest("  경주 여행  "));

        assertThat(response.title()).isEqualTo("경주 여행");
        assertThat(response.inviteCode()).hasSize(6);
        assertThat(response.status()).hasToString("OPEN");
    }
}
