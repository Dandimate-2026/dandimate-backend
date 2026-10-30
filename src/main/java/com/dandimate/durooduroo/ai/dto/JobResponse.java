/** OpenAPI Job 스키마입니다. 마감·다시 엮기·작업 조회가 같은 형식을 쓴다. */
package com.dandimate.durooduroo.ai.dto;

import com.dandimate.durooduroo.ai.domain.AiJob;
import java.time.LocalDateTime;

public record JobResponse(String id, String type, String status, String errorCode,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static JobResponse from(AiJob job) {
        return new JobResponse(job.getId(), job.getType(), job.getStatus(), job.getErrorCode(),
                job.getCreatedAt(), job.getUpdatedAt());
    }
}
