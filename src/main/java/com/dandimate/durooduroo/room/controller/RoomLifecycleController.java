/** 명세서 기준 방 홈·초대 토큰 재발급·방 마감 API입니다. */
package com.dandimate.durooduroo.room.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import jakarta.transaction.Transactional;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomLifecycleController {
    private final RoomRepository rooms; private final MemberRepository members;
    public RoomLifecycleController(RoomRepository rooms,MemberRepository members){this.rooms=rooms;this.members=members;}

    @GetMapping("/{roomId}")
    public ApiResponse<?> home(@PathVariable String roomId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){
        var room=rooms.findById(roomId).orElseThrow(()->new BusinessException("ROOM_NOT_FOUND","존재하지 않는 방입니다."));
        var me=members.findByRoomIdAndMemberToken(roomId,MemberTokenResolver.resolve(authorization,memberToken)).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 조회할 수 있습니다."));
        var list=members.findAllByRoomId(roomId).stream().map(m->Map.of("id",m.getId(),"nickname",m.getNickname(),"role",m.getRole().name())).toList();
        return ApiResponse.success(Map.of("room",Map.of("id",room.getId(),"name",room.getTitle(),"status",room.getStatus().name(),"deadlineAt",room.getCreatedAt().plusHours(48)),"me",Map.of("id",me.getId(),"nickname",me.getNickname(),"role",me.getRole().name()),"members",list,"memberCount",list.size()));
    }

    @PostMapping("/{roomId}/invite-token/rotate") @Transactional
    public ApiResponse<?> rotate(@PathVariable String roomId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){
        var room=rooms.findById(roomId).orElseThrow(()->new BusinessException("ROOM_NOT_FOUND","존재하지 않는 방입니다.")); var me=members.findByRoomIdAndMemberToken(roomId,MemberTokenResolver.resolve(authorization,memberToken)).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 사용할 수 있습니다."));
        if(me.getRole()!=MemberRole.HOST) throw new BusinessException("HOST_ONLY","방장만 초대 토큰을 재발급할 수 있습니다.");
        room.rotateInviteCode(UUID.randomUUID().toString().replace("-","").substring(0,6).toUpperCase()); rooms.save(room); return ApiResponse.success(Map.of("inviteToken",room.getInviteCode()));
    }

    @PostMapping("/{roomId}/close") @Transactional
    public ApiResponse<?> close(@PathVariable String roomId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){
        var room=rooms.findById(roomId).orElseThrow(()->new BusinessException("ROOM_NOT_FOUND","존재하지 않는 방입니다.")); var me=members.findByRoomIdAndMemberToken(roomId,MemberTokenResolver.resolve(authorization,memberToken)).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 사용할 수 있습니다."));
        if(me.getRole()!=MemberRole.HOST) throw new BusinessException("HOST_ONLY","방장만 방을 마감할 수 있습니다."); room.close(); rooms.save(room); return ApiResponse.success(Map.of("roomStatus","WEAVING","job",Map.of("status","QUEUED","type","WEAVE_SCROLL")));
    }
}
