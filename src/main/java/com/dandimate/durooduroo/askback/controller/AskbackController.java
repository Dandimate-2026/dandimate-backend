/** 명세서 기준 되묻기 목록·상세·답변·기억 안 나요·나중에·데모 실행 API입니다. */
package com.dandimate.durooduroo.askback.controller;

import com.dandimate.durooduroo.job.dto.JobResponse;
import com.dandimate.durooduroo.askback.dto.AskbackAnswerRequest;
import com.dandimate.durooduroo.askback.service.AskbackService;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AskbackController {

    private final AskbackService askbackService;
    private final boolean demoEnabled;

    public AskbackController(AskbackService askbackService, @Value("${app.demo-enabled:false}") boolean demoEnabled) {
        this.askbackService = askbackService;
        this.demoEnabled = demoEnabled;
    }

    @GetMapping("/rooms/{roomId}/askbacks/me")
    public ApiResponse<?> mine(@PathVariable String roomId, @RequestHeader(required = false) String authorization,
                               @RequestParam(required = false) String status) {
        return ApiResponse.success(Map.of("askbacks", askbackService.mine(roomId, authorization, status)));
    }

    @GetMapping("/askbacks/{askbackId}")
    public ApiResponse<?> detail(@PathVariable String askbackId, @RequestHeader(required = false) String authorization) {
        return ApiResponse.success(Map.of("askback", askbackService.detail(askbackId, authorization)));
    }

    @PostMapping("/askbacks/{askbackId}/answer")
    public ResponseEntity<ApiResponse<?>> answer(@PathVariable String askbackId, @RequestHeader(required = false) String authorization,
                                                 @Valid @RequestBody AskbackAnswerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(askbackService.answer(askbackId, authorization, request)));
    }

    @PostMapping("/askbacks/{askbackId}/forgot")
    public ApiResponse<?> forgot(@PathVariable String askbackId, @RequestHeader(required = false) String authorization) {
        var askback = askbackService.forgot(askbackId, authorization);
        return ApiResponse.success(Map.of("askback", Map.of("id", askback.getId(), "status", askback.getStatus())));
    }

    @PostMapping("/askbacks/{askbackId}/postpone")
    public ApiResponse<?> postpone(@PathVariable String askbackId, @RequestHeader(required = false) String authorization) {
        var askback = askbackService.postpone(askbackId, authorization);
        Map<String, Object> body = new HashMap<>();
        body.put("id", askback.getId());
        body.put("status", askback.getStatus());
        body.put("postponedAt", askback.getPostponedAt());
        return ApiResponse.success(Map.of("askback", body));
    }

    /** 데모 즉시 실행(FR-H9). app.demo-enabled일 때만 허용한다. */
    @PostMapping("/rooms/{roomId}/askbacks/run")
    public ResponseEntity<ApiResponse<?>> run(@PathVariable String roomId, @RequestHeader(required = false) String authorization) {
        if (!demoEnabled) throw new BusinessException(ErrorCode.FORBIDDEN, "데모 기능은 사용할 수 없습니다.");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(Map.of("job", JobResponse.from(askbackService.runDemo(roomId, authorization)))));
    }
}
