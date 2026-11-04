package com.dandimate.durooduroo.chat.dto;

import java.util.List;

/** 기존 ApiResponse로 감싸 반환할 챗봇 응답 본문. */
public record ChatMessageResponse(String answer, List<ChatSourceView> sources) {
    public ChatMessageResponse { sources = List.copyOf(sources); }
}
