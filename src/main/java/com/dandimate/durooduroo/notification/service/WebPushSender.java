package com.dandimate.durooduroo.notification.service;

import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.domain.PushSubscription;
import com.dandimate.durooduroo.notification.repository.PushSubscriptionRepository;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 앱 알림을 모든 등록 기기로 전달한다. VAPID 값이 없는 개발 환경에서는 조용히 건너뛰며,
 * 404·410 응답 구독은 다음 발송 전에 자동으로 제거한다.
 */
@Component
public class WebPushSender {
    private final PushSubscriptionRepository subscriptions;
    private final MemberRepository members;
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private volatile PushService push;

    public WebPushSender(PushSubscriptionRepository subscriptions, MemberRepository members,
                         @Value("${app.push.vapid-public-key:}") String publicKey,
                         @Value("${app.push.vapid-private-key:}") String privateKey,
                         @Value("${app.push.vapid-subject:mailto:admin@example.com}") String subject) {
        this.subscriptions = subscriptions;
        this.members = members;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(PushNotificationEvent event) {
        if (publicKey.isBlank() || privateKey.isBlank()) return;
        Notification notification = event.notification();
        String userId = members.findById(notification.getMemberId()).map(m -> m.getUserId()).orElse(null);
        if (userId == null) return;
        String payload = payload(notification);
        for (PushSubscription subscription : subscriptions.findAllByUserId(userId)) {
            try {
                int status = service().send(new nl.martijndwars.webpush.Notification(subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), payload.getBytes(StandardCharsets.UTF_8))).getStatusLine().getStatusCode();
                if (status == 404 || status == 410) subscriptions.delete(subscription);
            } catch (Exception ignored) {
                // 네트워크 일시 실패는 다음 앱 알림에서 다시 시도한다. 구독 자체는 보존한다.
            }
        }
    }

    private PushService service() throws Exception {
        PushService current = push;
        if (current != null) return current;
        synchronized (this) {
            if (push == null) {
                Security.addProvider(new BouncyCastleProvider());
                push = new PushService(publicKey, privateKey, subject);
            }
            return push;
        }
    }

    private String payload(Notification notification) {
        String title = switch (notification.getType()) {
            case "SCROLL_READY" -> "두루마리가 완성되었어요";
            case "REPLY_APPLIED" -> "새 답가가 반영되었어요";
            default -> "두루두루 알림";
        };
        String body = notification.getMessage() == null ? title : notification.getMessage();
        return "{\"title\":\"" + escape(title) + "\",\"body\":\"" + escape(body) + "\",\"type\":\"" + escape(notification.getType()) + "\",\"resourceType\":\"" + escape(notification.getResourceType()) + "\",\"resourceId\":\"" + escape(notification.getResourceId()) + "\"}";
    }
    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
