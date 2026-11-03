/** 도메인 규칙 위반을 표현하는 공통 비즈니스 예외다. 새 코드는 ErrorCode 생성자를 사용한다. */
package com.dandimate.durooduroo.common.exception;

import java.util.Map;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    /** 기존 문자열 코드 호환용. ErrorCode.from으로 명세 코드에 매핑된다. */
    public BusinessException(String code, String message) {
        this(ErrorCode.from(code), message, null);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.name();
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
