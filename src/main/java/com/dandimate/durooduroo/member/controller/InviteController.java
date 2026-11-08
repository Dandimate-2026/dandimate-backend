/** OpenAPI 명세서 기준 초대 링크 확인과 입장 API입니다. */
package com.dandimate.durooduroo.member.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
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
    private final com.dandimate.durooduroo.user.service.ConsentService consents;

    public InviteController(RoomRepository rooms, MemberRepository members, MemberService memberService, com.dandimate.durooduroo.user.service.ConsentService consents) {
        this.rooms = rooms;
        this.members = members;
        this.memberService = memberService;
        this.consents = consents;
    }

    @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.room.invite.RoomInviteService invites;
    @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.room.cover.RoomCoverService covers;
    @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.reviewerdemo.repository.ReviewerDemoSessionRepository demoSessions;
    @GetMapping("/{inviteToken}")
    public ApiResponse<?> preview(@PathVariable String inviteToken) {
        invites.remember(inviteToken);
        var room = rooms.findByInviteToken(inviteToken)
                .orElseThrow(() -> new BusinessException("INVITE_NOT_FOUND", "유효하지 않은 초대 토큰입니다."));
        var roomMembers = members.findAllByRoomIdAndLeftAtIsNull(room.getId());
        long count = roomMembers.size();
        String userId = CurrentUser.idOrNull();
        boolean alreadyJoined = userId != null
                && roomMembers.stream().anyMatch(member -> userId.equals(member.getUserId()));
        var data = new java.util.LinkedHashMap<String, Object>();
        data.put("roomId", room.getId());
        data.put("inviterNickname",roomMembers.stream().filter(m->m.getRole()==com.dandimate.durooduroo.member.domain.MemberRole.HOST).map(m->m.getNickname()).findFirst().orElse(null));
        data.put("coverImageUrl",room.getCoverPhotoId()==null?null:org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/invites/").path(inviteToken).path("/cover").toUriString());
        data.put("alreadyJoined", alreadyJoined);
        data.put("roomName", room.getTitle());
        data.put("meetingDate", room.getMeetingDate());
        data.put("status", room.getStatus().name());
        data.put("memberCount", count);
        data.put("flowers", roomMembers.stream().map(m -> m.getFlowerCode()).sorted().map(Enum::name).toList());
        data.put("capacity", room.getCapacity());
        boolean kicked=userId!=null && members.findByRoomIdAndUserId(room.getId(),userId).map(m->m.isKicked()).orElse(false);
        data.put("canJoin", !demoSessions.existsByDemoRoomId(room.getId()) && !kicked && count < room.getCapacity());
        return ApiResponse.success(data);
    }

    @GetMapping("/{inviteToken}/cover")
    public ResponseEntity<?> cover(@PathVariable String inviteToken) {
        var room=rooms.findByInviteToken(inviteToken).orElseThrow(()->new BusinessException("INVITE_NOT_FOUND","초대 링크가 없습니다."));
        return covers.inviteImage(room.getCoverPhotoId());
    }
    @PostMapping("/{inviteToken}/join")
    public ResponseEntity<ApiResponse<SpecJoinRoomResponse>> join(@PathVariable String inviteToken,
                                                                   @Valid @RequestBody SpecJoinRoomRequest request) {
        try {
            request.consent().validate();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("CONSENT_REQUIRED", e.getMessage());
        }
        consents.recordIfUser(CurrentUser.idOrNull(), request.consent());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memberService.joinByInvite(inviteToken, request)));
    }
}
