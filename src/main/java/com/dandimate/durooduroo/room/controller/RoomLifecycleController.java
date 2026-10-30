/** 명세서 기준 방 홈·초대 토큰 재발급·방 마감 API입니다. */
package com.dandimate.durooduroo.room.controller;

import com.dandimate.durooduroo.job.dto.JobResponse;
import com.dandimate.durooduroo.job.service.WeaveJobService;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import jakarta.transaction.Transactional;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomLifecycleController {
    private final RoomRepository rooms; private final MemberRepository members; private final WeaveJobService weaveJobs; private final com.dandimate.durooduroo.room.service.RoomHomeService roomHome; private final com.dandimate.durooduroo.room.service.RoomAdminService roomAdmin; private final boolean demoEnabled;
    public RoomLifecycleController(RoomRepository rooms,MemberRepository members,WeaveJobService weaveJobs,com.dandimate.durooduroo.room.service.RoomHomeService roomHome,com.dandimate.durooduroo.room.service.RoomAdminService roomAdmin,@Value("${app.demo-enabled:false}") boolean demoEnabled){this.rooms=rooms;this.members=members;this.weaveJobs=weaveJobs;this.roomHome=roomHome;this.roomAdmin=roomAdmin;this.demoEnabled=demoEnabled;}

    @GetMapping("/{roomId}")
    public ApiResponse<?> home(@PathVariable String roomId,@RequestHeader(required=false) String authorization){
        return ApiResponse.success(roomHome.home(roomId,authorization));
    }

    /** 방장: 이름·모임 날짜·마감 수정 (마감은 WEAVING 전 한 번, 생성 후 24~72시간). */
    @PatchMapping("/{roomId}")
    public ApiResponse<?> update(@PathVariable String roomId,@RequestHeader(required=false) String authorization,@RequestBody Map<String,String> body){
        var room=roomAdmin.update(roomId,authorization,body.get("name"),body.get("meetingDate")==null?null:java.time.LocalDate.parse(body.get("meetingDate")),
                body.get("deadlineAt")==null?null:java.time.OffsetDateTime.parse(body.get("deadlineAt")));
        var summary=new HashMap<String,Object>(); summary.put("id",room.getId()); summary.put("name",room.getTitle()); summary.put("meetingDate",room.getMeetingDate()); summary.put("status",room.getStatus().name()); summary.put("deadlineAt",room.getDeadlineAt());
        return ApiResponse.success(Map.of("room",summary));
    }

    /** 방장: 방과 모든 데이터 삭제. 저장소 파일은 MEDIA_DELETE 작업이 지운다. */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> delete(@PathVariable String roomId,@RequestHeader(required=false) String authorization){
        roomAdmin.delete(roomId,authorization);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/invite-token/rotate") @Transactional
    public ApiResponse<?> rotate(@PathVariable String roomId,@RequestHeader(required=false) String authorization){
        var room=rooms.findById(roomId).orElseThrow(()->new BusinessException("ROOM_NOT_FOUND","존재하지 않는 방입니다.")); var me=members.findByRoomIdAndMemberToken(roomId,MemberTokenResolver.resolve(authorization)).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 사용할 수 있습니다."));
        if(me.getRole()!=MemberRole.HOST) throw new BusinessException("HOST_ONLY","방장만 초대 토큰을 재발급할 수 있습니다.");
        String inviteToken=room.rotateInviteToken(); rooms.save(room); return ApiResponse.success(Map.of("inviteToken",inviteToken));
    }

    /** 조기·데모 마감. body의 mode(EARLY/DEMO)는 필수이며, DEMO는 app.demo-enabled일 때만 허용한다. */
    @PostMapping("/{roomId}/close")
    public ResponseEntity<ApiResponse<?>> close(@PathVariable String roomId,@RequestHeader(required=false) String authorization,@RequestBody Map<String,String> body){
        rooms.findById(roomId).orElseThrow(()->new BusinessException("ROOM_NOT_FOUND","존재하지 않는 방입니다.")); var me=members.findByRoomIdAndMemberToken(roomId,MemberTokenResolver.resolve(authorization)).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 사용할 수 있습니다."));
        if(me.getRole()!=MemberRole.HOST) throw new BusinessException("HOST_ONLY","방장만 방을 마감할 수 있습니다.");
        String mode=body.get("mode");
        if(!"EARLY".equals(mode)&&!"DEMO".equals(mode)) throw new BusinessException(ErrorCode.VALIDATION_ERROR,"mode는 EARLY 또는 DEMO여야 합니다.");
        if(mode.equals("DEMO")&&!demoEnabled) throw new BusinessException(ErrorCode.FORBIDDEN,"데모 마감은 사용할 수 없습니다.");
        var job=weaveJobs.close(roomId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(Map.of("job",JobResponse.from(job))));
    }
}
