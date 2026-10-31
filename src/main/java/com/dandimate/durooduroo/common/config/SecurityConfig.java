package com.dandimate.durooduroo.common.config;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.KakaoUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.web.cors.*;
import java.util.List;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, KakaoUserService users, ObjectMapper json,
            org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient<org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest> tokenClient,
            @Value("${KAKAO_CLIENT_ID:}") String clientId,
            @Value("${KAKAO_CLIENT_SECRET:}") String secret,
            @Value("${KAKAO_REDIRECT_URI:}") String redirect,
            @Value("${APP_FRONTEND_URL:http://localhost:3000}") String frontend) throws Exception {
        http.cors(c -> {}).csrf(c -> c.ignoringRequestMatchers(
            org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults()
                .matcher(HttpMethod.PUT, "/api/v1/photos/*/upload")))
            .authorizeHttpRequests(a -> a
            .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/auth/csrf", "/api/v1/invites/*",
                "/api/v1/rooms/*/events", "/api/v1/photos/*/image", "/api/v1/entries/*/audio").permitAll()
            .requestMatchers(HttpMethod.PUT, "/api/v1/photos/*/upload").permitAll()
            .requestMatchers("/swagger-ui.html", "/openapi/**", "/webjars/**", "/oauth2/**", "/login/**", "/error").permitAll()
            .anyRequest().authenticated())
            .requestCache(c -> c.disable())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(), ApiResponse.failure("UNAUTHORIZED", "로그인이 필요합니다."));
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(), ApiResponse.failure("FORBIDDEN", "접근 권한 또는 CSRF 토큰을 확인해 주세요."));
                }))
            .logout(l -> l.logoutUrl("/api/v1/auth/logout").invalidateHttpSession(true)
                .deleteCookies("JSESSIONID").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
        if (!clientId.isBlank() && !secret.isBlank() && !redirect.isBlank()) {
            var kakao = ClientRegistration.withRegistrationId("kakao")
                .clientId(clientId).clientSecret(secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirect)
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id").clientName("카카오").build();
            http.oauth2Login(o -> o.clientRegistrationRepository(new InMemoryClientRegistrationRepository(kakao))
                .tokenEndpoint(t -> t.accessTokenResponseClient(tokenClient))
                .userInfoEndpoint(u -> u.userService(users))
                .successHandler((req, res, auth) -> res.sendRedirect(frontend))
                .failureHandler((req, res, ex) -> {
                    res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(), ApiResponse.failure("OAUTH_LOGIN_FAILED", "카카오 인증에 실패했습니다. 다시 로그인해 주세요."));
                }));
        }
        return http.build();
    }
    @Bean
    org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient<org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest> kakaoTokenClient() {
        return new org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient();
    }

    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${APP_FRONTEND_URL:http://localhost:3000}") String frontend) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontend)); config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "Idempotency-Key", "If-Match"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); return source;
    }
}
