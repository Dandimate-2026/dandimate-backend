/**
 * 시각을 계약서의 date-time(RFC 3339, 오프셋 포함)으로 내보낸다. 엔티티는 LocalDateTime(서버 시간대)을 쓰므로
 * 서버 시간대 오프셋을 붙여 "2026-10-26T21:30:00+09:00"처럼 직렬화한다.
 */
package com.dandimate.durooduroo.common.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer offsetDateTimes() {
        return builder -> builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
                generator.writeString(value.atZone(ZoneId.systemDefault()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
        });
    }
}
