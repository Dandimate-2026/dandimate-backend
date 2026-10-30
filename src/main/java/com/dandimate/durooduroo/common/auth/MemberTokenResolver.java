/** 명세서의 Authorization: Bearer {memberAccessToken} 헤더에서 토큰을 꺼낸다. URL·본문으로 받는 토큰은 로그에 남을 수 있어 지원하지 않는다. */
package com.dandimate.durooduroo.common.auth;

import com.dandimate.durooduroo.common.exception.BusinessException;

public final class MemberTokenResolver {
    private MemberTokenResolver() {}
    public static String resolve(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ") && authorization.length() > 7) return authorization.substring(7);
        throw new BusinessException("UNAUTHORIZED", "memberAccessToken이 필요합니다.");
    }
}
