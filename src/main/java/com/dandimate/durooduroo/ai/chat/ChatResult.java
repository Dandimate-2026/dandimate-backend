package com.dandimate.durooduroo.ai.chat;

import java.util.List;

/** citedEntryIds가 실제 입력 근거에 속하는지 서비스에서 검증한 뒤 응답한다. */
public record ChatResult(String answer, List<String> citedEntryIds) {
    public ChatResult { citedEntryIds = List.copyOf(citedEntryIds); }
}
