package com.dandimate.durooduroo.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 명세 0장 "공통 오류 코드" 표와 ErrorCode가 코드·HTTP 상태까지 정확히 같은지 확인한다. INTERNAL_ERROR(500)만 서버 전용으로 더 있다. */
class ErrorCodeTableTest {

    private static final Map<String, Integer> SPEC = new LinkedHashMap<>();

    static {
        SPEC.put("VALIDATION_ERROR", 400);
        SPEC.put("CONSENT_REQUIRED", 400);
        SPEC.put("INVALID_MEMBER_TOKEN", 401);
        SPEC.put("FORBIDDEN", 403);
        SPEC.put("ROOM_NOT_FOUND", 404);
        SPEC.put("RESOURCE_NOT_FOUND", 404);
        SPEC.put("ROOM_FULL", 409);
        SPEC.put("NICKNAME_TAKEN", 409);
        SPEC.put("ROOM_WEAVING", 409);
        SPEC.put("INVALID_ROOM_STATE", 409);
        SPEC.put("LOCKED", 409);
        SPEC.put("VERSION_CONFLICT", 409);
        SPEC.put("ASKBACK_CLOSED", 409);
        SPEC.put("IDEMPOTENCY_KEY_REUSED", 409);
        SPEC.put("FILE_TOO_LARGE", 413);
        SPEC.put("UNSUPPORTED_MEDIA_TYPE", 415);
        SPEC.put("MIN_DURATION_NOT_MET", 422);
        SPEC.put("MAX_DURATION_EXCEEDED", 422);
        SPEC.put("UPLOAD_VERIFICATION_FAILED", 422);
        SPEC.put("LIMIT_REACHED", 422);
        SPEC.put("STT_FAILED", 502);
        SPEC.put("AI_INVALID_OUTPUT", 502);
        SPEC.put("AI_TEMPORARILY_UNAVAILABLE", 503);
        SPEC.put("AI_TIMEOUT", 504);
        SPEC.put("PAYMENT_NOT_CONFIGURED", 503);
        SPEC.put("PLACE_SEARCH_UNAVAILABLE", 503);
        SPEC.put("PAYMENT_IN_PROGRESS", 409);
        SPEC.put("PAYMENT_REJECTED", 422);
        SPEC.put("PAYMENT_UNCERTAIN", 503);
    }

    @Test
    void 명세_오류_코드표와_코드와_HTTP_상태가_같다() {
        Map<String, Integer> actual = Arrays.stream(ErrorCode.values()).filter(c -> c != ErrorCode.INTERNAL_ERROR)
                .collect(Collectors.toMap(Enum::name, c -> c.status().value(), (x, y) -> x, LinkedHashMap::new));
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(SPEC);
        assertThat(ErrorCode.INTERNAL_ERROR.status().value()).isEqualTo(500);
    }

    /** 소스에 남은 문자열 오류 코드가 모두 명세 코드로 매핑되는지 확인한다. 빠지면 VALIDATION_ERROR로 조용히 바뀌기 때문이다. */
    @Test
    void 소스의_문자열_오류_코드는_모두_명세_코드로_매핑된다() throws Exception {
        Pattern literal = Pattern.compile("BusinessException\\(\"([A-Z_]+)\"");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            var unmapped = files.filter(f -> f.toString().endsWith(".java")).flatMap(f -> {
                try {
                    return literal.matcher(Files.readString(f)).results().map(m -> m.group(1));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).filter(code -> !ErrorCode.isMapped(code)).distinct().toList();
            assertThat(unmapped).isEmpty();
        }
    }
}
