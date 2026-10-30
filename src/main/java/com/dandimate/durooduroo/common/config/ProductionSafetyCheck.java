/**
 * mysql 프로필(운영·스테이징)로 띄웠을 때 위험한 설정을 시작 로그에 경고한다. 서버는 막지 않는다.
 * 데모 기능 켜짐, 로컬 파일 저장소, 서명 키 미설정(재시작하면 사진·음성 URL 무효), 가짜 STT, 스키마 자동 변경.
 */
package com.dandimate.durooduroo.common.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql")
public class ProductionSafetyCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);

    private final boolean demoEnabled;
    private final String storageType;
    private final String signingSecret;
    private final String sttProvider;
    private final String aiProvider;
    private final String ddlAuto;
    private final String datasourceUrl;

    public ProductionSafetyCheck(@Value("${app.demo-enabled:false}") boolean demoEnabled, @Value("${storage.type:local}") String storageType,
                                 @Value("${app.media.signing-secret:}") String signingSecret, @Value("${app.stt.provider:openai}") String sttProvider,
                                 @Value("${app.ai.provider:rule}") String aiProvider, @Value("${spring.jpa.hibernate.ddl-auto:}") String ddlAuto,
                                 @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.demoEnabled = demoEnabled;
        this.storageType = storageType;
        this.signingSecret = signingSecret;
        this.sttProvider = sttProvider;
        this.aiProvider = aiProvider;
        this.ddlAuto = ddlAuto;
        this.datasourceUrl = datasourceUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warn() {
        for (String warning : warnings()) log.warn("[운영 설정 점검] {}", warning);
    }

    List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        if (demoEnabled) warnings.add("DEMO_ENABLED=true: 데모 마감·되묻기 즉시 실행이 켜져 있습니다.");
        if (!"s3".equals(storageType)) warnings.add("STORAGE_TYPE=" + storageType + ": 사진·음성이 서버 디스크에 저장됩니다.");
        if (!"s3".equals(storageType) && signingSecret.isBlank()) warnings.add("MEDIA_SIGNING_SECRET 미설정: 재시작하면 기존 사진·음성 URL이 무효가 됩니다.");
        if ("fake".equals(sttProvider)) warnings.add("STT_PROVIDER=fake: 음성 받아쓰기가 고정 문장을 돌려줍니다.");
        if ("rule".equals(aiProvider)) warnings.add("AI_PROVIDER=rule: 되묻기·두루마리가 규칙 기반 임시 구현으로 동작합니다.");
        if ("update".equals(ddlAuto) || "create".equals(ddlAuto)) warnings.add("JPA_DDL_AUTO=" + ddlAuto + ": 운영에서는 validate와 마이그레이션 도구를 권장합니다.");
        if (datasourceUrl.contains("useSSL=false")) warnings.add("DB 연결이 TLS 없이(useSSL=false) 이뤄집니다.");
        return warnings;
    }
}
