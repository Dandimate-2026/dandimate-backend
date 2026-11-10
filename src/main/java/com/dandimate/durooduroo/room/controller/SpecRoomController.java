/** OpenAPI 명세서 기준 방 생성 API입니다. */
package com.dandimate.durooduroo.room.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
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
    private final com.dandimate.durooduroo.room.service.RoomDetailsService details;
    private final com.dandimate.durooduroo.user.service.ConsentService consents;

    public SpecRoomController(RoomRepository rooms, MemberRepository members, com.dandimate.durooduroo.room.service.RoomDetailsService details, com.dandimate.durooduroo.user.service.ConsentService consents) {
        this.details = details;
        this.rooms = rooms;
        this.members = members;
        this.consents = consents;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<SpecCreateRoomResponse>> create(@Valid @RequestBody SpecCreateRoomRequest request) {
        try {
            request.consent().validate();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.CONSENT_REQUIRED, e.getMessage());
        }
        // 유료 플랜은 결제 검증이 연결되기 전까지 생성하지 않는다. FREE로 대체하지 않는다.
        if (request.planType().requiresPayment()) {
            throw new BusinessException(com.dandimate.durooduroo.common.exception.ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "유료 플랜 결제 기능이 아직 연결되지 않았습니다.");
        }
        Room room = rooms.save(Room.create(request.name().trim(), request.meetingDate(), request.selectedCapacity(), request.planType()));
        if (request.deadlineAt() != null)
            room.changeDeadline(request.deadlineAt().atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime(), java.time.LocalDateTime.now());
        details.apply(room, request.place(), request.selectedCover(), com.dandimate.durooduroo.common.auth.CurrentUser.idOrNull());
        Member host = members.save(Member.host(room.getId(), request.hostNickname().trim(), com.dandimate.durooduroo.common.auth.CurrentUser.idOrNull()));
        consents.recordIfUser(com.dandimate.durooduroo.common.auth.CurrentUser.idOrNull(), request.consent());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SpecCreateRoomResponse.from(room, host)));
    }
}
