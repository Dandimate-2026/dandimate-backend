package com.dandimate.durooduroo.user.service;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.chat.repository.ChatMessageRepository;
import com.dandimate.durooduroo.notification.repository.PushSubscriptionRepository;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.member.service.MembershipService;
import com.dandimate.durooduroo.reviewerdemo.service.ReviewerDemoService;
import com.dandimate.durooduroo.reviewerdemo.repository.ReviewerDemoSessionRepository;
import com.dandimate.durooduroo.user.repository.UserAccountRepository;
import com.dandimate.durooduroo.user.repository.UserConsentRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Revokes Kakao login and removes account-private data while preserving shared room history. */
@Service
public class AccountWithdrawalService {
    private final UserAccountRepository users;
    private final UserConsentRepository consents;
    private final PushSubscriptionRepository pushSubscriptions;
    private final ChatMessageRepository chatMessages;
    private final ReviewerDemoService reviewerDemo;
    private final ReviewerDemoSessionRepository demoSessions;
    private final MemberRepository members;
    private final MembershipService memberships;
    private final EntityManager entityManager;

    public AccountWithdrawalService(UserAccountRepository users, UserConsentRepository consents,
            PushSubscriptionRepository pushSubscriptions, ChatMessageRepository chatMessages,
            ReviewerDemoService reviewerDemo, ReviewerDemoSessionRepository demoSessions,
            MemberRepository members, MembershipService memberships, EntityManager entityManager) {
        this.users = users;
        this.consents = consents;
        this.pushSubscriptions = pushSubscriptions;
        this.chatMessages = chatMessages;
        this.reviewerDemo = reviewerDemo;
        this.demoSessions = demoSessions;
        this.members = members;
        this.memberships = memberships;
        this.entityManager = entityManager;
    }

    @Transactional
    public void withdraw(String userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_MEMBER_TOKEN, "로그인 정보를 확인해 주세요."));

        boolean ownsActiveRoom = members.findAllByUserIdAndLeftAtIsNull(userId).stream()
                .anyMatch(member -> member.getRole() == MemberRole.HOST
                        && !demoSessions.existsByDemoRoomId(member.getRoomId()));
        if (ownsActiveRoom) {
            throw new BusinessException(ErrorCode.LOCKED,
                    "방장인 모임방의 방장을 넘기거나 방을 삭제한 뒤 탈퇴해 주세요.");
        }

        // Demo rooms and their derived records belong to the account's private demo scope.
        reviewerDemo.setEnabled(userId, false);
        // End ordinary room memberships so withdrawn accounts no longer occupy seats.
        members.findAllByUserIdAndLeftAtIsNull(userId).stream()
                .filter(member -> member.getRole() != MemberRole.HOST)
                .map(com.dandimate.durooduroo.member.domain.Member::getRoomId)
                .distinct().forEach(memberships::leave);
        chatMessages.deleteAllByUserId(userId);
        entityManager.createNativeQuery("DELETE FROM chat_usage WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM chat_wallets WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();
        consents.deleteAllByUserId(userId);
        pushSubscriptions.deleteAllByUserId(userId);

        // Keep the users row referenced by historical room authors and shared cover images,
        // but erase the Kakao subject so a later login starts as a new account.
        user.withdraw();
    }
}
