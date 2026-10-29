/** 명세서 기준 두루마리 다시 엮기·작업 상태 API입니다. MVP에서는 동기 실행 후 결과 상태를 반환합니다. */
package com.dandimate.durooduroo.ai.controller;

import com.dandimate.durooduroo.ai.domain.AiJob;
import com.dandimate.durooduroo.ai.repository.AiJobRepository;
import com.dandimate.durooduroo.ai.service.AiParagraphService;
import com.dandimate.durooduroo.ai.service.WeaveJobService;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
import java.util.HashMap;

@RestController
public class AiJobController {
    private final AiJobRepository jobs; private final AiParagraphService paragraphs; private final WeaveJobService weaveJobs; private final MemberRepository members;
    public AiJobController(AiJobRepository jobs,AiParagraphService paragraphs,WeaveJobService weaveJobs,MemberRepository members){this.jobs=jobs;this.paragraphs=paragraphs;this.weaveJobs=weaveJobs;this.members=members;}
    @PostMapping("/api/v1/rooms/{roomId}/weave-jobs")
    public ResponseEntity<ApiResponse<?>> create(@PathVariable String roomId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){ paragraphs.requireHost(roomId,authorization,memberToken); AiJob job=weaveJobs.reweave(roomId); return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(java.util.Map.of("id",job.getId(),"type",job.getType(),"status",job.getStatus()))); }
    /** 같은 방 참여자만 조회하며, ASKBACK_ANALYZE 작업은 공개하지 않는다. */
    @GetMapping("/api/v1/jobs/{jobId}")
    public ApiResponse<?> get(@PathVariable String jobId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){ String token=MemberTokenResolver.resolve(authorization,memberToken); AiJob job=jobs.findById(jobId).filter(j->!AiJob.ASKBACK_ANALYZE.equals(j.getType())).orElseThrow(()->new BusinessException("JOB_NOT_FOUND","작업이 없습니다.")); members.findByRoomIdAndMemberToken(job.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 조회할 수 있습니다.")); var data=new HashMap<String,Object>(); data.put("id",job.getId()); data.put("type",job.getType()); data.put("status",job.getStatus()); data.put("errorCode",job.getErrorCode()); data.put("createdAt",job.getCreatedAt()); data.put("updatedAt",job.getUpdatedAt()); return ApiResponse.success(data); }
}
