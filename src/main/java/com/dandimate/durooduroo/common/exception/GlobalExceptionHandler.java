/** 애플리케이션 예외를 명세서 공통 오류 응답과 HTTP 상태로 변환한다. */
package com.dandimate.durooduroo.common.exception;

import com.dandimate.durooduroo.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return respond(exception.getErrorCode(), exception.getMessage(), exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return respond(ErrorCode.VALIDATION_ERROR, message, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MissingRequestHeaderException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return respond(ErrorCode.VALIDATION_ERROR, "요청 형식이 올바르지 않습니다.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return respond(ErrorCode.FILE_TOO_LARGE, "파일 크기 제한을 초과했습니다.", null);
    }

    /** 404·405·415 같은 Spring MVC 표준 예외와 예상하지 못한 예외를 처리한다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) return respond(ErrorCode.RESOURCE_NOT_FOUND, "요청한 리소스가 없습니다.", null);
            if (status == HttpStatus.UNSUPPORTED_MEDIA_TYPE) return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 형식입니다.", null);
            if (status != null && status.is4xxClientError()) {
                return ResponseEntity.status(status).body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), "요청을 처리할 수 없습니다."));
            }
        }
        log.error("Unhandled exception", exception);
        return respond(ErrorCode.INTERNAL_ERROR, "일시적인 오류가 발생했습니다.", null);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message, BusinessException exception) {
        return ResponseEntity.status(code.status())
                .body(ApiResponse.failure(code.name(), message, exception == null ? null : exception.getDetails()));
    }
}
