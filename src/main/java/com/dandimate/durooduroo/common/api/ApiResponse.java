/** 명세서 공통 응답 형식: 성공은 {data, meta}, 실패는 {error}만 내려준다. */
package com.dandimate.durooduroo.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Meta meta, ErrorBody error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, new Meta(RequestIdFilter.currentRequestId()), null);
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return failure(code, message, null);
    }

    public static ApiResponse<Void> failure(String code, String message, Map<String, Object> details) {
        return new ApiResponse<>(null, null, new ErrorBody(code, message, details, RequestIdFilter.currentRequestId()));
    }

    public record Meta(String requestId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, Map<String, Object> details, String requestId) {
    }
}
