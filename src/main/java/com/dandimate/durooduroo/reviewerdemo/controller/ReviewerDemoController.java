package com.dandimate.durooduroo.reviewerdemo.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.reviewerdemo.dto.ReviewerDemoView;
import com.dandimate.durooduroo.reviewerdemo.service.ReviewerDemoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviewer-demo")
public class ReviewerDemoController {
    private final ReviewerDemoService demos;

    public ReviewerDemoController(ReviewerDemoService demos) { this.demos = demos; }

    /** 카카오 로그인한 심사자의 계정에 샘플 방 세 개를 한 번만 연결한다. */
    @org.springframework.web.bind.annotation.GetMapping
    public ApiResponse<ReviewerDemoView> state() { return ApiResponse.success(demos.state(CurrentUser.requireId())); }

    public record Toggle(@jakarta.validation.constraints.NotNull Boolean enabled) {}
    @org.springframework.web.bind.annotation.PutMapping
    public ApiResponse<ReviewerDemoView> toggle(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody Toggle request) {
        return ApiResponse.success(demos.setEnabled(CurrentUser.requireId(),request.enabled()));
    }

    /** Compatibility alias; new clients use PUT on the ON/OFF switch. */
    @PostMapping("/activate")
    public ApiResponse<ReviewerDemoView> activate() {
        return ApiResponse.success(demos.activate(CurrentUser.requireId()));
    }
}
