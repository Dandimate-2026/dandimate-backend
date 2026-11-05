package com.dandimate.durooduroo.payment;

import com.dandimate.durooduroo.common.exception.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TossPaymentGatewayTest {
    private static final String DONE = "{\"paymentKey\":\"key\",\"orderId\":\"order123\",\"currency\":\"KRW\",\"status\":\"DONE\",\"totalAmount\":2900,\"balanceAmount\":2900}";

    @Test void lookupThenConfirmUsesBasicAuthAndStableIdempotencyKey() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var approvals = new AtomicInteger();
        var validHeaders = new java.util.concurrent.atomic.AtomicBoolean();
        server.createContext("/v1/payments", exchange -> {
            boolean confirm = exchange.getRequestURI().getPath().endsWith("/confirm");
            if (confirm) {
                approvals.incrementAndGet();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                validHeaders.set("approve-order123".equals(exchange.getRequestHeaders().getFirst("Idempotency-Key"))
                        && exchange.getRequestHeaders().getFirst("Authorization").startsWith("Basic ")
                        && body.contains("2900") && body.contains("order123"));
            }
            byte[] body = (confirm ? DONE : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(confirm ? 200 : 404, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            gateway(server).approve("key", "order123", 2900);
            assertThat(approvals.get()).isEqualTo(1);
            assertThat(validHeaders.get()).isTrue();
        } finally { server.stop(0); }
    }

    @Test void alreadyApprovedLookupRecoversWithoutSecondApprovalAndMismatchIsRejected() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var posts = new AtomicInteger();
        server.createContext("/v1/payments", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) posts.incrementAndGet();
            byte[] body = DONE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            gateway(server).approve("key", "order123", 2900);
            assertThat(posts.get()).isZero();
            assertThatThrownBy(() -> gateway(server).approve("key", "another-order", 2900))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_UNCERTAIN));
            assertThatThrownBy(() -> gateway(server).approve("key", "order123", 1)).isInstanceOf(BusinessException.class);
            assertThat(posts.get()).isZero();
        } finally { server.stop(0); }
    }

    @Test void liveOrMissingKeysDoNotEnablePayments() {
        assertThat(new TossPaymentGateway("", "http://127.0.0.1").configured()).isFalse();
        assertThat(new TossPaymentGateway("live_sk_example_not_a_real_key", "http://127.0.0.1").configured()).isFalse();
    }

    private TossPaymentGateway gateway(HttpServer server) {
        return new TossPaymentGateway("test_sk_fixture_not_a_real_key", "http://127.0.0.1:" + server.getAddress().getPort());
    }
}
