package com.dandimate.durooduroo.user.service;
import com.dandimate.durooduroo.user.domain.UserAccount;
import com.dandimate.durooduroo.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
@Service
public class KakaoAccountService {
    private final UserAccountRepository users;
    public KakaoAccountService(UserAccountRepository users) { this.users = users; }
    public String findOrCreate(String kakaoId) {
        var existing = users.findByProviderAndProviderUserId("KAKAO", kakaoId);
        if (existing.isPresent()) return existing.get().getId();
        try { return users.saveAndFlush(UserAccount.kakao(kakaoId)).getId(); }
        catch (DataIntegrityViolationException concurrentSignup) {
            return users.findByProviderAndProviderUserId("KAKAO", kakaoId).orElseThrow(() -> concurrentSignup).getId();
        }
    }
}
