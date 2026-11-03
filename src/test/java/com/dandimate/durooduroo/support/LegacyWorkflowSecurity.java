package com.dandimate.durooduroo.support;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
/** 기존 익명/참가자 토큰 시나리오의 업무 로직 회귀 테스트 전용. 운영 빌드에 포함되지 않는다. */
@TestConfiguration
public class LegacyWorkflowSecurity {
    @Bean @Order(0)
    SecurityFilterChain legacyWorkflowSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher(request -> true).authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable()).build();
    }
}
