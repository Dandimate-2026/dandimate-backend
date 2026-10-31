/**
 * 명세서 멱등성 규칙을 적용한다.
 * - 계약서에 Idempotency-Key가 필수인 POST 요청은 키가 없으면 400 VALIDATION_ERROR.
 * - 같은 사용자·경로·키로 24시간 안에 다시 오면 최초 응답(상태·본문)을 그대로 돌려준다.
 * - 세션 계정 ID로 요청을 구분한다. 기존 비인증 테스트만 본문/헤더 해시를 사용한다.
 * - 같은 키에 다른 본문이 오면 409 IDEMPOTENCY_KEY_REUSED. 아직 처리 중인 키도 409로 거절한다.
 * - 5xx 응답은 저장하지 않아 같은 키로 다시 시도할 수 있다.
 * multipart 요청은 컨테이너가 파트를 직접 읽어야 하므로 본문 대신 Content-Type·길이로 지문을 만든다.
 */
package com.dandimate.durooduroo.common.idempotency;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
// Spring Security(-100) 이후 실행: 계정별 응답 격리 및 인증 전 재생 방지.
@Order(-90)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Duration RETENTION = Duration.ofHours(24);

    /** openapi.yaml에서 IdempotencyKey 파라미터를 가진 POST 경로. */
    private static final List<String> PATTERNS = List.of(
            "/api/v1/rooms",
            "/api/v1/invites/*/join",
            "/api/v1/rooms/*/invite-token/rotate",
            "/api/v1/rooms/*/close",
            "/api/v1/rooms/*/photos/upload-requests",
            "/api/v1/photos/*/complete",
            "/api/v1/photos/*/entries",
            "/api/v1/photos/*/first-entry/skip",
            "/api/v1/askbacks/*/answer",
            "/api/v1/askbacks/*/forgot",
            "/api/v1/rooms/*/askbacks/run",
            "/api/v1/rooms/*/weave-jobs",
            "/api/v1/paragraphs/*/replies");
    private static final List<String> ANONYMOUS_PATTERNS = List.of("/api/v1/rooms", "/api/v1/invites/*/join");

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final IdempotencyRecordRepository records;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyRecordRepository records, ObjectMapper objectMapper) {
        this.records = records;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || PATTERNS.stream().noneMatch(p -> matcher.match(p, request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.length() < 16 || key.length() > 128) {
            writeError(response, ErrorCode.VALIDATION_ERROR, "Idempotency-Key 헤더(16~128자)가 필요합니다.");
            return;
        }

        String path = request.getRequestURI();
        boolean multipart = request.getContentType() != null && request.getContentType().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
        HttpServletRequest body = multipart ? request : new CachedBodyRequest(request);
        String bodyHash = multipart
                ? Tokens.hash("multipart:" + request.getContentType().split(";")[0] + ":" + request.getContentLengthLong())
                : Tokens.hash(new String(((CachedBodyRequest) body).body, StandardCharsets.UTF_8));
        String requestHash = Tokens.hash(request.getMethod() + " " + path + " " + bodyHash);
        boolean anonymous = ANONYMOUS_PATTERNS.stream().anyMatch(p -> matcher.match(p, path));
        String userId = com.dandimate.durooduroo.common.auth.CurrentUser.idOrNull();
        String scope = userId != null ? "user:" + userId : anonymous ? "anonymous:" + bodyHash : Tokens.hash(String.valueOf(request.getHeader("Authorization")));

        Optional<IdempotencyRecord> existing = findLive(scope, request.getMethod(), path, key);
        if (existing.isPresent()) {
            replayOrReject(existing.get(), requestHash, response);
            return;
        }
        IdempotencyRecord record;
        try {
            record = records.saveAndFlush(IdempotencyRecord.start(scope, request.getMethod(), path, key, requestHash));
        } catch (DataIntegrityViolationException raced) {
            // 같은 키의 요청이 동시에 들어와 다른 요청이 먼저 기록했다.
            Optional<IdempotencyRecord> winner = findLive(scope, request.getMethod(), path, key);
            if (winner.isPresent()) replayOrReject(winner.get(), requestHash, response);
            else writeError(response, ErrorCode.IDEMPOTENCY_KEY_REUSED, "같은 요청을 처리 중이에요.");
            return;
        }

        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(body, captured);
            if (captured.getStatus() < 500) {
                record.complete(captured.getStatus(), captured.getContentType(), captured.getContentAsByteArray());
                records.save(record);
                stored = true;
            }
        } finally {
            if (!stored) records.deleteById(record.getId());
            captured.copyBodyToResponse();
        }
    }

    @Scheduled(fixedDelayString = "PT1H")
    public void deleteExpired() {
        records.deleteCreatedBefore(LocalDateTime.now().minus(RETENTION));
    }

    private Optional<IdempotencyRecord> findLive(String scope, String method, String path, String key) {
        Optional<IdempotencyRecord> found = records.findByScopeAndMethodAndPathAndKey(scope, method, path, key);
        if (found.isPresent() && found.get().getCreatedAt().isBefore(LocalDateTime.now().minus(RETENTION))) {
            records.delete(found.get());
            return Optional.empty();
        }
        return found;
    }

    private void replayOrReject(IdempotencyRecord record, String requestHash, HttpServletResponse response) throws IOException {
        if (!record.getRequestHash().equals(requestHash)) {
            writeError(response, ErrorCode.IDEMPOTENCY_KEY_REUSED, "같은 Idempotency-Key로 다른 요청을 보냈어요.");
        } else if (!record.isCompleted()) {
            writeError(response, ErrorCode.IDEMPOTENCY_KEY_REUSED, "같은 요청을 처리 중이에요.");
        } else {
            response.setStatus(record.getResponseStatus());
            if (record.getResponseContentType() != null) response.setContentType(record.getResponseContentType());
            response.setHeader(REPLAYED_HEADER, "true");
            if (record.getResponseBody() != null) response.getOutputStream().write(record.getResponseBody());
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(code.name(), message));
    }

    /** 본문을 한 번 읽어 해시에 쓰고, 컨트롤러가 다시 읽을 수 있게 보관한다. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
