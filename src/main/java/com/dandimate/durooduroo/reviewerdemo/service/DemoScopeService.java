package com.dandimate.durooduroo.reviewerdemo.service;

import com.dandimate.durooduroo.reviewerdemo.repository.ReviewerDemoSessionRepository;
import com.dandimate.durooduroo.user.domain.UserAccount;
import com.dandimate.durooduroo.common.exception.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;

/** Scope is persisted per account, shared by all devices. REAL history survives demo cycles. */
@Service
public class DemoScopeService {
    private final ReviewerDemoSessionRepository sessions;
    @PersistenceContext private EntityManager em;
    public DemoScopeService(ReviewerDemoSessionRepository sessions) { this.sessions=sessions; }
    public String current(String userId) { return sessions.findByUserId(userId).map(s->s.getId()).orElse("REAL"); }
    public void lock(String userId) {
        if(em.find(UserAccount.class,userId,LockModeType.PESSIMISTIC_WRITE)==null)
            throw new BusinessException(ErrorCode.INVALID_MEMBER_TOKEN,"로그인이 필요해요.");
    }
    public void requireCurrent(String userId,String expected) {
        lock(userId);
        if(!current(userId).equals(expected)) throw new BusinessException(ErrorCode.VERSION_CONFLICT,"데모 상태가 바뀌었어요. 다시 질문해 주세요.");
    }
}
