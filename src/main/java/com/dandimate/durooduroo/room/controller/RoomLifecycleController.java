/** 명세서 기준 방 홈·고정 초대 링크 조회·방 마감 API입니다. */
package com.dandimate.durooduroo.room.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.job.dto.JobResponse;
import com.dandimate.durooduroo.job.service.WeaveJobService;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.dto.CoverPhotoView;
import com.dandimate.durooduroo.room.dto.RoomUpdateRequest;
import com.dandimate.durooduroo.room.invite.RoomInviteService;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.room.service.RoomAdminService;
import com.dandimate.durooduroo.room.service.RoomHomeService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomLifecycleController {

    private final RoomRepository rooms;
    private final MemberRepository members;
    private final WeaveJobService weaveJobs;
    private final RoomHomeService roomHome;
    private final RoomAdminService roomAdmin;
    private final RoomInviteService invites;
    private final boolean demoEnabled;

    public RoomLifecycleController(RoomRepository rooms, MemberRepository members, WeaveJobService weaveJobs,
                                   RoomHomeService roomHome, RoomAdminService roomAdmin, RoomInviteService invites,
                                   @Value("${app.demo-enabled:false}") boolean demoEnabled) {
        this.rooms = rooms;
        this.members = members;
        this.weaveJobs = weaveJobs;
        this.roomHome = roomHome;
        this.roomAdmin = roomAdmin;
        this.invites = invites;
        this.demoEnabled = demoEnabled;
    }

    @GetMapping("/{roomId}")
    public ApiResponse<?> home(@PathVariable String roomId, @RequestHeader(required = false) String authorization) {
        return ApiResponse.success(roomHome.home(roomId, authorization));
    }

    /** 방장: 이름·모임 날짜·마감 수정 (마감은 엮기 전 미래 시각으로 횟수 제한 없이 변경). */
    @PatchMapping("/{roomId}")
    public ApiResponse<?> update(@PathVariable String roomId,
                                 @RequestHeader(required = false) String authorization,
                                 @Valid @RequestBody RoomUpdateRequest body) {
        Room room = roomAdmin.update(roomId, authorization, body.name, body.meetingDate, body.deadlineAt,
                body.placePresent(), body.place(), body.coverPresent(), body.coverPhoto(), body.capacity,
                body.scrollTemplateId(), body.scrollTemplatePresent());
        return ApiResponse.success(Map.of("room", summaryOf(room)));
    }

    /** 방장: 방과 모든 데이터 삭제. 저장소 파일은 MEDIA_DELETE 작업이 지운다. */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> delete(@PathVariable String roomId, @RequestHeader(required = false) String authorization) {
        roomAdmin.delete(roomId, authorization);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{roomId}/invite")
    public ApiResponse<?> invite(@PathVariable String roomId, @RequestHeader(required = false) String authorization) {
        return ApiResponse.success(Map.of("inviteToken", invites.get(roomId, authorization)));
    }

    /** 조기·데모 마감. body의 mode(EARLY/DEMO)는 필수이며, DEMO는 app.demo-enabled일 때만 허용한다. */
    @PostMapping("/{roomId}/close")
    public ResponseEntity<ApiResponse<?>> close(@PathVariable String roomId,
                                                @RequestHeader(required = false) String authorization,
                                                @RequestBody Map<String, String> body) {
        rooms.requireById(roomId);
        var me = members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
        if (me.getRole() != MemberRole.HOST) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 방을 마감할 수 있습니다.");
        }
        String mode = body.get("mode");
        if (!"EARLY".equals(mode) && !"DEMO".equals(mode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "mode는 EARLY 또는 DEMO여야 합니다.");
        }
        if (mode.equals("DEMO") && !demoEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "데모 마감은 사용할 수 없습니다.");
        }
        var job = weaveJobs.close(roomId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(Map.of("job", JobResponse.from(job))));
    }

    /** 값이 null일 수 있는 필드가 있어 Map.of 대신 HashMap을 쓴다. */
    private static Map<String, Object> summaryOf(Room room) {
        var summary = new HashMap<String, Object>();
        summary.put("id", room.getId());
        summary.put("name", room.getTitle());
        summary.put("meetingDate", room.getMeetingDate());
        summary.put("status", room.getStatus().name());
        summary.put("deadlineAt", room.getDeadlineAt());
        summary.put("place", room.getPlace());
        summary.put("coverPhoto", CoverPhotoView.from(room.getCoverPhotoId()));
        summary.put("capacity", room.getCapacity());
        summary.put("scrollTemplateId", room.getScrollTemplateId());
        summary.put("createdAt", room.getCreatedAt());
        summary.put("deleteAt", room.getDeleteAt());
        summary.put("deadlineChangedByHost", room.isDeadlineChangedByHost());
        summary.put("coverImageUrl", room.getCoverImageUrl());
        return summary;
    }
}
