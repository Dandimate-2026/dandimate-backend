package com.dandimate.durooduroo.chat.service;

import com.dandimate.durooduroo.ai.chat.ChatCommand;
import com.dandimate.durooduroo.chat.dto.ChatMessageRequest;
import com.dandimate.durooduroo.chat.dto.ChatMessageResponse;
import com.dandimate.durooduroo.chat.domain.ChatMessage;
import com.dandimate.durooduroo.chat.repository.ChatMessageRepository;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Bounded, session-local context. Client-supplied messages never supply trusted history. */
@Service
public class ChatConversationService {
    private static final String ATTRIBUTE = ChatConversationService.class.getName();
    private final ChatService chat;
    private final ChatMessageRepository messages;

    @org.springframework.beans.factory.annotation.Autowired
    public ChatConversationService(ChatService chat, ChatMessageRepository messages) { this.chat = chat; this.messages = messages; }
    /** 단위 테스트와 기존 호출부 호환용: 이 생성자는 세션 대화만 유지한다. */
    public ChatConversationService(ChatService chat) { this.chat = chat; this.messages = null; }

    public ChatMessageResponse answer(String userId, ChatMessageRequest request, HttpSession session) {
        if (request.conversationId() == null) return chat.answer(userId, request.message());
        synchronized (session) {
            State state = (State) session.getAttribute(ATTRIBUTE);
            if (state == null || !state.userId.equals(userId)) state = new State(userId);
            List<ChatCommand.Turn> history = state.conversations.getOrDefault(request.conversationId(), List.of());
            ChatMessageResponse response = chat.answer(userId, request.message(), List.copyOf(history));
            if (messages != null) {
                messages.save(ChatMessage.of(userId, request.conversationId(), "USER", request.message()));
                messages.save(ChatMessage.of(userId, request.conversationId(), "ASSISTANT", response.answer()));
            }
            if (response.outcome().equals("SMALL_TALK") || response.outcome().equals("OUT_OF_SCOPE")) return response;
            List<ChatCommand.Turn> updated = new ArrayList<>(history);
            updated.add(new ChatCommand.Turn("judgment_" + UUID.randomUUID(), response.context(),
                    response.citations().stream().map(ChatMessageResponse.Citation::entryId).distinct().toList(), response.decision()));
            if (updated.size() > 8) updated = new ArrayList<>(updated.subList(updated.size() - 8, updated.size()));
            state.conversations.remove(request.conversationId());
            state.conversations.put(request.conversationId(), List.copyOf(updated));
            while (state.conversations.size() > 4) state.conversations.remove(state.conversations.keySet().iterator().next());
            session.setAttribute(ATTRIBUTE, state);
            return response;
        }
    }

    public List<ChatMessage> history(String userId, String conversationId) {
        return messages == null ? List.of() : messages.findAllByUserIdAndConversationIdOrderByCreatedAtAsc(userId, conversationId);
    }
    public List<String> conversations(String userId) { return messages == null ? List.of() : messages.findConversationIdsByUserId(userId); }
    public void deleteHistory(String userId, String conversationId) { if (messages != null) messages.deleteByUserIdAndConversationId(userId, conversationId); }

    private static final class State {
        final String userId;
        final LinkedHashMap<String, List<ChatCommand.Turn>> conversations = new LinkedHashMap<>();
        State(String userId) { this.userId = userId; }
    }
}
