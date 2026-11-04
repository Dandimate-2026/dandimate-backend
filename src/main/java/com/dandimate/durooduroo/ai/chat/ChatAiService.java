package com.dandimate.durooduroo.ai.chat;

/** AI는 답변과 근거 ID만 생성한다. URL·작성자 상세정보는 서버가 DB에서 조립한다. */
public interface ChatAiService {
    ChatResult answer(ChatCommand command);
}
