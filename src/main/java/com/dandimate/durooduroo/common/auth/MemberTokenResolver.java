/** 기존 서비스 호출 형식과 세션 인증을 연결한다.
 * 실제 HTTP 요청은 SecurityConfig가 세션 인증을 강제한다.
 * Bearer 해석은 이전 업무 회귀 테스트를 위한 호환 경로이며 외부 로그인 수단이 아니다.
 */
package com.dandimate.durooduroo.common.auth;

import com.dandimate.durooduroo.common.exception.BusinessException;

public final class MemberTokenResolver {
    private MemberTokenResolver() {}
    public static String resolve(String authorization) {
        if (CurrentUser.idOrNull() != null) return "session";
        if (authorization != null && authorization.startsWith("Bearer ") && authorization.length() > 7) return authorization.substring(7);
        throw new BusinessException("UNAUTHORIZED", "memberAccessToken이 필요합니다.");
    }
}
