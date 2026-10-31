package com.dandimate.durooduroo.user.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 설정이 있으면 Spring Security가 먼저 처리한다. 미설정 시 키 없이 로그인시키지 않는다. */
@RestController
public class OAuthUnavailableController {
    @GetMapping({"/oauth2/authorization/kakao", "/login/oauth2/code/kakao"})
    public ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).body(ApiResponse.failure("OAUTH_NOT_CONFIGURED",
                "서버의 카카오 로그인 설정이 필요합니다."));
    }
}
