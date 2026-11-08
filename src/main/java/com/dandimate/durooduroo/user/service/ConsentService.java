package com.dandimate.durooduroo.user.service;

import com.dandimate.durooduroo.room.dto.ConsentRequest;
import com.dandimate.durooduroo.user.domain.UserConsent;
import com.dandimate.durooduroo.user.repository.UserConsentRepository;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 사용자의 약관 버전과 동의 시각을 보관한다. 초대만으로 들어온 익명 참여자는 로그인 뒤 다시 기록된다. */
@Service
public class ConsentService {
    private final UserConsentRepository consents;
    private final String currentVersion;
    public ConsentService(UserConsentRepository consents, @Value("${app.consent.current-version:v1}") String currentVersion) { this.consents = consents; this.currentVersion = currentVersion; }
    @Transactional
    public void recordIfUser(String userId, ConsentRequest request) {
        if (!currentVersion.equals(request.version())) throw new BusinessException(ErrorCode.CONSENT_REQUIRED, "최신 약관에 다시 동의해 주세요.");
        if (userId == null || consents.existsByUserIdAndVersion(userId, request.version())) return;
        consents.save(UserConsent.of(userId, request.version(), request.terms(), request.mediaProcessing(), request.externalAiProcessing()));
    }
    public Current current(String userId) { var consent=userId==null?null:consents.findByUserIdAndVersion(userId,currentVersion).orElse(null); return new Current(currentVersion,consent!=null,consent==null?null:consent.getConsentedAt()); }
    public record Current(String version, boolean consented, java.time.LocalDateTime consentedAt) {}
}
