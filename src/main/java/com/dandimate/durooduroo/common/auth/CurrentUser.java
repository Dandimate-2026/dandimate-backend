package com.dandimate.durooduroo.common.auth;
import org.springframework.security.core.context.SecurityContextHolder;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
public final class CurrentUser {
    private CurrentUser() {}
    public static String idOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AccountPrincipal p ? p.getName() : null;
    }
    public static String requireId() {
        String id = idOrNull();
        if (id == null) throw new BusinessException(ErrorCode.INVALID_MEMBER_TOKEN, "로그인이 필요합니다.");
        return id;
    }
}
