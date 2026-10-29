/** 명세서의 Bearer memberAccessToken을 추출하고 MVP 하위 호환 토큰을 지원합니다. */
package com.dandimate.durooduroo.common.auth;

import com.dandimate.durooduroo.common.exception.BusinessException;

public final class MemberTokenResolver {
    private MemberTokenResolver() {}
    public static String resolve(String authorization, String legacyToken) {
        if (authorization != null && authorization.startsWith("Bearer ")) return authorization.substring(7);
        if (legacyToken != null && !legacyToken.isBlank()) return legacyToken;
        throw new BusinessException("UNAUTHORIZED", "memberAccessToken이 필요합니다.");
    }
}
