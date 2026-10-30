/** 명세서 공통 오류 코드와 HTTP 상태입니다. 기존 코드 문자열은 aliases로 명세 코드에 매핑합니다. */
package com.dandimate.durooduroo.common.exception;

import java.util.Arrays;
import java.util.Set;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "EMPTY_CONTENT", "INVALID_SCORE", "INVALID_AUDIO"),
    CONSENT_REQUIRED(HttpStatus.BAD_REQUEST),
    INVALID_MEMBER_TOKEN(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "STREAM_TOKEN_INVALID"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "MEMBER_FORBIDDEN", "HOST_ONLY"),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "ENTRY_NOT_FOUND", "ASKBACK_NOT_FOUND",
            "PARAGRAPH_NOT_FOUND", "REPLY_NOT_FOUND", "NOTIFICATION_NOT_FOUND", "JOB_NOT_FOUND", "SCROLL_NOT_FOUND"),
    ROOM_FULL(HttpStatus.CONFLICT),
    NICKNAME_TAKEN(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME"),
    ROOM_WEAVING(HttpStatus.CONFLICT),
    INVALID_ROOM_STATE(HttpStatus.CONFLICT, "ROOM_CLOSED"),
    LOCKED(HttpStatus.CONFLICT),
    VERSION_CONFLICT(HttpStatus.CONFLICT),
    ASKBACK_CLOSED(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE"),
    MIN_DURATION_NOT_MET(HttpStatus.UNPROCESSABLE_ENTITY),
    MAX_DURATION_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    UPLOAD_VERIFICATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_LIMIT", "DUPLICATE_ENTRY"),
    STT_FAILED(HttpStatus.BAD_GATEWAY, "STT_EMPTY"),
    AI_INVALID_OUTPUT(HttpStatus.BAD_GATEWAY),
    AI_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "STT_NOT_CONFIGURED", "WEAVE_FAILED"),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;
    private final Set<String> aliases;

    ErrorCode(HttpStatus status, String... aliases) {
        this.status = status;
        this.aliases = Set.of(aliases);
    }

    public HttpStatus status() {
        return status;
    }

    /** 명세 코드나 기존 코드 문자열을 명세 코드로 바꾼다. 알 수 없는 코드는 VALIDATION_ERROR로 본다. */
    public static ErrorCode from(String code) {
        return Arrays.stream(values())
                .filter(value -> value.name().equals(code) || value.aliases.contains(code))
                .findFirst()
                .orElse(VALIDATION_ERROR);
    }
}
