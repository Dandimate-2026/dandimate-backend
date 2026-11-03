/**
 * ai 패키지가 던지는 공통 실패입니다. 도메인 서비스가 code를 보고 공개 오류 코드로 바꾼다 (명세 5장 오류 표).
 * 요청·응답 원문은 메시지에 넣지 않는다.
 */
package com.dandimate.durooduroo.ai.common;

public class AiFailure extends RuntimeException {

    public enum Code { INVALID_INPUT, NOT_CONFIGURED, DOWNLOAD_FAILED, OPENAI_RATE_LIMITED, OPENAI_TIMEOUT, OPENAI_FAILED, INVALID_OUTPUT }

    private final Code code;
    private final boolean retryable;

    public AiFailure(Code code, boolean retryable, String message) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public Code getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
