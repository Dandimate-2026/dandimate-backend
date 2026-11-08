package com.dandimate.durooduroo.user.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.user.service.ConsentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 앱이 표시해야 할 약관 버전과 로그인 사용자의 재동의 여부를 돌려준다. */
@RestController
@RequestMapping("/api/v1/consents")
public class ConsentController {
    private final ConsentService consents;
    public ConsentController(ConsentService consents) { this.consents = consents; }
    @GetMapping("/current")
    public ApiResponse<?> current() { return ApiResponse.success(consents.current(CurrentUser.idOrNull())); }
}
