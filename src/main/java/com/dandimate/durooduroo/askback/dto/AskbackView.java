/** OpenAPI Askback 스키마입니다. confidence·reason·근거 원문 본문은 내보내지 않고 근거 작성자 닉네임만 보낸다. */
package com.dandimate.durooduroo.askback.dto;

import com.dandimate.durooduroo.memory.dto.EntryView;
import com.dandimate.durooduroo.photo.dto.PhotoView;
import java.time.LocalDateTime;
import java.util.List;

public record AskbackView(String id, PhotoView photo, String gapType, String question, List<String> evidenceNicknames,
                          String status, EntryView firstEntry, LocalDateTime createdAt, LocalDateTime expiresAt) {
}
