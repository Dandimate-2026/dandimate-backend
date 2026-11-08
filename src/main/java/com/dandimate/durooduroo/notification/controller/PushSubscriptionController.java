package com.dandimate.durooduroo.notification.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.notification.domain.PushSubscription;
import com.dandimate.durooduroo.notification.repository.PushSubscriptionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

/** 브라우저 푸시 구독을 사용자·기기별로 보관한다. 실제 발송은 VAPID 키를 설정한 뒤 별도 전송기로 연결한다. */
@RestController
@RequestMapping("/api/v1/push")
public class PushSubscriptionController {
    private final PushSubscriptionRepository subscriptions;
    private final String vapidPublicKey;

    public PushSubscriptionController(PushSubscriptionRepository subscriptions,
                                      @Value("${app.push.vapid-public-key:}") String vapidPublicKey) {
        this.subscriptions = subscriptions;
        this.vapidPublicKey = vapidPublicKey;
    }

    @GetMapping("/vapid-public-key")
    public ApiResponse<?> key() {
        return ApiResponse.success(new PushConfig(!vapidPublicKey.isBlank(), vapidPublicKey.isBlank() ? null : vapidPublicKey));
    }

    @PostMapping("/subscriptions")
    @Transactional
    public ApiResponse<?> subscribe(@Valid @RequestBody SubscriptionRequest request) {
        String userId = CurrentUser.requireId();
        subscriptions.findByEndpoint(request.endpoint()).ifPresentOrElse(
                current -> current.refresh(userId, request.keys().p256dh(), request.keys().auth()),
                () -> subscriptions.save(PushSubscription.of(userId, request.endpoint(), request.keys().p256dh(), request.keys().auth())));
        return ApiResponse.success(java.util.Map.of("subscribed", true));
    }

    @DeleteMapping("/subscriptions")
    @Transactional
    public ApiResponse<?> unsubscribe(@Valid @RequestBody EndpointRequest request) {
        subscriptions.deleteByEndpointAndUserId(request.endpoint(), CurrentUser.requireId());
        return ApiResponse.success(java.util.Map.of("subscribed", false));
    }

    public record PushConfig(boolean enabled, String publicKey) {}
    public record SubscriptionRequest(@NotBlank String endpoint, @Valid Keys keys) {
        public SubscriptionRequest { if (keys == null) throw new BusinessException("VALIDATION_ERROR", "브라우저 푸시 키가 필요합니다."); }
    }
    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}
    public record EndpointRequest(@NotBlank String endpoint) {}
}
