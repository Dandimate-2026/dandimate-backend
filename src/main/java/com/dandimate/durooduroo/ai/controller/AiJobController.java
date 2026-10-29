/** 명세서 기준 두루마리 생성 작업 API입니다. MVP에서는 동기 실행 후 완료 상태를 반환합니다. */
package com.dandimate.durooduroo.ai.controller;

import com.dandimate.durooduroo.ai.domain.AiJob;
import com.dandimate.durooduroo.ai.repository.AiJobRepository;
import com.dandimate.durooduroo.ai.service.AiParagraphService;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.exception.BusinessException;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;

@RestController
public class AiJobController {
    private final AiJobRepository jobs; private final AiParagraphService paragraphs;
    public AiJobController(AiJobRepository jobs,AiParagraphService paragraphs){this.jobs=jobs;this.paragraphs=paragraphs;}
    @PostMapping("/api/v1/rooms/{roomId}/weave-jobs")
    public ResponseEntity<ApiResponse<?>> create(@PathVariable String roomId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){ paragraphs.requireHost(roomId,authorization,memberToken); AiJob job=jobs.save(AiJob.queued(roomId)); try{paragraphs.generate(roomId);}catch(RuntimeException e){job.fail();jobs.save(job);throw new BusinessException("WEAVE_FAILED","두루마리 생성에 실패했습니다.");} job.succeed(); jobs.save(job); return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(java.util.Map.of("id",job.getId(),"type","WEAVE_SCROLL","status",job.getStatus()))); }
    @GetMapping("/api/v1/jobs/{jobId}")
    public ApiResponse<?> get(@PathVariable String jobId){ AiJob job=jobs.findById(jobId).orElseThrow(()->new BusinessException("JOB_NOT_FOUND","작업이 없습니다.")); return ApiResponse.success(java.util.Map.of("id",job.getId(),"type","WEAVE_SCROLL","status",job.getStatus(),"createdAt",job.getCreatedAt(),"updatedAt",job.getUpdatedAt())); }
}
