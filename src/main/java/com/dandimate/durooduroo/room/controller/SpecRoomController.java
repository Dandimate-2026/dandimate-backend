/** OpenAPI 명세서 기준 방 생성 API입니다. */
package com.dandimate.durooduroo.room.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.dto.SpecCreateRoomRequest;
import com.dandimate.durooduroo.room.dto.SpecCreateRoomResponse;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
public class SpecRoomController {
    private final RoomRepository rooms;
    private final MemberRepository members;

    public SpecRoomController(RoomRepository rooms, MemberRepository members) {
        this.rooms = rooms;
        this.members = members;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<SpecCreateRoomResponse>> create(@Valid @RequestBody SpecCreateRoomRequest request) {
        try {
            request.consent().validate();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("CONSENT_REQUIRED", e.getMessage());
        }
        Room room = rooms.save(Room.create(request.name().trim(), request.meetingDate()));
        Member host = members.save(Member.host(room.getId(), request.hostNickname().trim()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SpecCreateRoomResponse.from(room, host)));
    }
}
