package com.dandimate.durooduroo.chat.service;

import com.dandimate.durooduroo.ai.chat.ChatCommand;
import com.dandimate.durooduroo.chat.dto.ChatMessageRequest;
import com.dandimate.durooduroo.chat.dto.ChatMessageResponse;
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

    public ChatConversationService(ChatService chat) { this.chat = chat; }

    public ChatMessageResponse answer(String userId, ChatMessageRequest request, HttpSession session) {
        if (request.conversationId() == null) return chat.answer(userId, request.message());
        synchronized (session) {
            State state = (State) session.getAttribute(ATTRIBUTE);
            if (state == null || !state.userId.equals(userId)) state = new State(userId);
            List<ChatCommand.Turn> history = state.conversations.getOrDefault(request.conversationId(), List.of());
            ChatMessageResponse response = chat.answer(userId, request.message(), List.copyOf(history));
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

    private static final class State {
        final String userId;
        final LinkedHashMap<String, List<ChatCommand.Turn>> conversations = new LinkedHashMap<>();
        State(String userId) { this.userId = userId; }
    }
}
