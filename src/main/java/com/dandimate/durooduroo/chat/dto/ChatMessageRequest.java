package com.dandimate.durooduroo.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사용자 ID나 접근 가능한 방 목록은 요청에서 신뢰하지 않고 인증 세션으로 확인한다. */
public record ChatMessageRequest(@NotBlank @Size(max = 2000) String message,
        @jakarta.validation.constraints.Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String conversationId) {
    public ChatMessageRequest(String message) { this(message, null); }
}
