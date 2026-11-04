package com.dandimate.durooduroo.payment;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Test keys only. No raw provider error/headers are exposed to clients or logs. */
@Component
public class TossPaymentGateway implements PaymentGateway {
    private final String secret;
    private final RestClient client;

    public TossPaymentGateway(@Value("${TOSS_SECRET_KEY:}") String secret,
                              @Value("${app.payment.base-url:https://api.tosspayments.com}") String baseUrl) {
        this.secret = secret;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeaders(h -> h.setBasicAuth(secret, "")).build();
    }

    public boolean configured() { return secret.startsWith("test_sk_") && secret.length() > 16; }

    @Override
    public void approve(String key, String order, int amount) {
        if (!configured()) throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED, "토스 테스트 결제 키 설정이 필요합니다.");
        try {
            // Query first: a previous approval may have succeeded before a network/DB failure.
            JsonNode existing = lookup(key);
            if (existing != null) {
                identity(existing, key, order, amount);
                if ("DONE".equals(existing.path("status").asText())) return;
                String status = existing.path("status").asText();
                if (!"IN_PROGRESS".equals(status) && !"READY".equals(status))
                    throw new BusinessException(ErrorCode.PAYMENT_REJECTED, "완료할 수 없는 결제입니다. 새 주문으로 다시 시도해 주세요.");
            }
            JsonNode result = client.post().uri("/v1/payments/confirm")
                    .header("Idempotency-Key", "approve-" + order)
                    .body(Map.of("paymentKey", key, "orderId", order, "amount", amount))
                    .retrieve().body(JsonNode.class);
            identity(result, key, order, amount);
            if (!"DONE".equals(result.path("status").asText()))
                throw new BusinessException(ErrorCode.PAYMENT_UNCERTAIN, "결제 완료 여부를 확인 중입니다. 같은 주문으로 다시 확인해 주세요.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            if (code >= 400 && code < 500 && code != 401 && code != 403 && code != 409 && code != 429)
                throw new BusinessException(ErrorCode.PAYMENT_REJECTED, "결제가 거절되었습니다. 새 주문으로 다시 시도해 주세요.");
            throw new BusinessException(ErrorCode.PAYMENT_UNCERTAIN, "결제 상태 확인이 필요합니다. 같은 주문으로 다시 확인해 주세요.");
        } catch (Exception ex) {
            // A provider error can race with approval; preserve PROCESSING until a safe retry reconciles it.
            throw new BusinessException(ErrorCode.PAYMENT_UNCERTAIN, "결제 상태를 확인하지 못했습니다. 같은 주문으로 다시 확인해 주세요.");
        }
    }

    private JsonNode lookup(String key) {
        try { return client.get().uri("/v1/payments/{key}", key).retrieve().body(JsonNode.class); }
        catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return null;
            throw ex;
        }
    }

    private void identity(JsonNode body, String key, String order, int amount) {
        if (body == null || !key.equals(body.path("paymentKey").asText())
                || !order.equals(body.path("orderId").asText()) || !body.path("totalAmount").isIntegralNumber()
                || amount != body.path("totalAmount").asLong() || !"KRW".equals(body.path("currency").asText())
                || ("DONE".equals(body.path("status").asText()) && amount != body.path("balanceAmount").asLong()))
            throw new BusinessException(ErrorCode.PAYMENT_UNCERTAIN, "결제 검증 정보가 일치하지 않습니다. 관리자 확인이 필요합니다.");
    }
}
