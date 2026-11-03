package com.dandimate.durooduroo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.dandimate.durooduroo.common.idempotency.IdempotencyFilter;
import java.util.UUID;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 테스트 요청마다 서로 다른 Idempotency-Key를 붙인다. 테스트가 직접 지정한 키는 그대로 둔다. */
@Configuration
public class IdempotencyKeyTestSupport {

    @Bean
    MockMvcBuilderCustomizer idempotencyKeyByDefault() {
        return builder -> builder.defaultRequest(get("/").with(request -> {
            if (request.getHeader(IdempotencyFilter.HEADER) == null) {
                request.addHeader(IdempotencyFilter.HEADER, UUID.randomUUID().toString());
            }
            return request;
        }));
    }
}
