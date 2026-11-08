package com.dandimate.durooduroo.user.service;

import com.dandimate.durooduroo.room.dto.ConsentRequest;
import com.dandimate.durooduroo.user.domain.UserConsent;
import com.dandimate.durooduroo.user.repository.UserConsentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 사용자의 약관 버전과 동의 시각을 보관한다. 초대만으로 들어온 익명 참여자는 로그인 뒤 다시 기록된다. */
@Service
public class ConsentService {
    private final UserConsentRepository consents;
    public ConsentService(UserConsentRepository consents) { this.consents = consents; }
    @Transactional
    public void recordIfUser(String userId, ConsentRequest request) {
        if (userId == null || consents.existsByUserIdAndVersion(userId, request.version())) return;
        consents.save(UserConsent.of(userId, request.version(), request.terms(), request.mediaProcessing(), request.externalAiProcessing()));
    }
}
