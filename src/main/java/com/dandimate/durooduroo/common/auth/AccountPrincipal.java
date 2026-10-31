package com.dandimate.durooduroo.common.auth;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
/** 세션에는 카카오 토큰이나 전체 프로필 대신 내부 계정 ID만 보관한다. */
public class AccountPrincipal extends DefaultOAuth2User {
    public AccountPrincipal(String userId) {
        super(Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("userId", userId), "userId");
    }
}
