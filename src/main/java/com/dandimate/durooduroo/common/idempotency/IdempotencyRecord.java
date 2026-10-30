/** Idempotency-Key별 최초 요청 지문과 응답을 24시간 보관한다. (scope, method, path, key)가 유일하다. */
package com.dandimate.durooduroo.common.idempotency;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_scope_key",
        columnNames = {"scope", "method", "path", "idempotency_key"}))
public class IdempotencyRecord {

    @Id
    @Column(length = 36)
    private String id;

    /** 참여자 토큰 해시, 또는 인증 전 요청이면 "anonymous:" + 본문 해시. */
    @Column(nullable = false, length = 80)
    private String scope;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 255)
    private String path;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String key;

    @Column(nullable = false, length = 64)
    private String requestHash;

    private Integer responseStatus;

    private String responseContentType;

    @Lob
    private byte[] responseBody;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(String scope, String method, String path, String key, String requestHash) {
        this.id = UUID.randomUUID().toString();
        this.scope = scope;
        this.method = method;
        this.path = path;
        this.key = key;
        this.requestHash = requestHash;
        this.createdAt = LocalDateTime.now();
    }

    public static IdempotencyRecord start(String scope, String method, String path, String key, String requestHash) {
        return new IdempotencyRecord(scope, method, path, key, requestHash);
    }

    public void complete(int status, String contentType, byte[] body) {
        this.responseStatus = status;
        this.responseContentType = contentType;
        this.responseBody = body;
    }

    public boolean isCompleted() {
        return responseStatus != null;
    }

    public String getId() { return id; }
    public String getRequestHash() { return requestHash; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseContentType() { return responseContentType; }
    public byte[] getResponseBody() { return responseBody; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
