/** OpenAPI 명세서 기준 초대 링크 확인과 입장 API입니다. */
package com.dandimate.durooduroo.member.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.dto.SpecJoinRoomRequest;
import com.dandimate.durooduroo.member.dto.SpecJoinRoomResponse;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.member.service.MemberService;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invites")
public class InviteController {
    private final RoomRepository rooms;
    private final MemberRepository members;
    private final MemberService memberService;

    public InviteController(RoomRepository rooms, MemberRepository members, MemberService memberService) {
        this.rooms = rooms;
        this.members = members;
        this.memberService = memberService;
    }

    @GetMapping("/{inviteToken}")
    public ApiResponse<?> preview(@PathVariable String inviteToken) {
        var room = rooms.findByInviteToken(inviteToken)
                .orElseThrow(() -> new BusinessException("INVITE_NOT_FOUND", "유효하지 않은 초대 토큰입니다."));
        var roomMembers = members.findAllByRoomId(room.getId());
        long count = roomMembers.size();
        var data = new java.util.LinkedHashMap<String, Object>();
        data.put("roomName", room.getTitle());
        data.put("meetingDate", room.getMeetingDate());
        data.put("status", room.getStatus().name());
        data.put("memberCount", count);
        data.put("flowers", roomMembers.stream().map(m -> m.getFlowerCode()).sorted().map(Enum::name).toList());
        data.put("capacity", com.dandimate.durooduroo.room.domain.Room.CAPACITY);
        data.put("canJoin", count < com.dandimate.durooduroo.room.domain.Room.CAPACITY);
        return ApiResponse.success(data);
    }

    @PostMapping("/{inviteToken}/join")
    public ResponseEntity<ApiResponse<SpecJoinRoomResponse>> join(@PathVariable String inviteToken,
                                                                   @Valid @RequestBody SpecJoinRoomRequest request) {
        try {
            request.consent().validate();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("CONSENT_REQUIRED", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memberService.joinByInvite(inviteToken, request)));
    }
}
