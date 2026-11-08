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
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.dandimate.durooduroo.reviewerdemo.service.DemoScopeService scopes;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.transaction.support.TransactionTemplate tx;
    private final ChatService chat;
    private final ChatMessageRepository messages;

    @org.springframework.beans.factory.annotation.Autowired
    public ChatConversationService(ChatService chat, ChatMessageRepository messages) { this.chat = chat; this.messages = messages; }
    /** 단위 테스트와 기존 호출부 호환용: 이 생성자는 세션 대화만 유지한다. */
    public ChatConversationService(ChatService chat) { this.chat = chat; this.messages = null; }

    public ChatMessageResponse answer(String userId, ChatMessageRequest request, HttpSession session) {
        String conversationId = request.conversationId() == null || request.conversationId().isBlank() ? UUID.randomUUID().toString() : request.conversationId();
        synchronized (session) {
            State state = (State) session.getAttribute(ATTRIBUTE);
            if (state == null || !state.userId.equals(userId)) state = new State(userId);
            String scope=scopes==null?"REAL":scopes.current(userId);
            String contextKey=scope+":"+conversationId;
            // Purge retired demo contexts on every device; retain real context.
            state.conversations.keySet().removeIf(k->!k.startsWith("REAL:")&&!k.startsWith(scope+":"));
            List<ChatCommand.Turn> history = state.conversations.getOrDefault(contextKey, List.of());
            ChatMessageResponse response = chat.answer(userId, request.message(), List.copyOf(history));
            if (messages != null) {
                Runnable persist=()->{
                    if(scopes!=null) scopes.requireCurrent(userId,scope);
                    var question=ChatMessage.of(userId,conversationId,"USER",request.message()); question.setDemoScope(scope);
                    var answer=ChatMessage.of(userId,conversationId,"ASSISTANT",response.answer()); answer.setDemoScope(scope);
                    answer.setSourceRoomIds(response.sources().stream().map(source->source.room().roomId()).distinct().sorted().collect(java.util.stream.Collectors.joining(",")));
                    messages.save(question);messages.save(answer);
                };
                if(tx!=null) tx.executeWithoutResult(status->persist.run()); else persist.run();
            }
            if (response.outcome().equals("SMALL_TALK") || response.outcome().equals("OUT_OF_SCOPE")) return response.withConversationId(conversationId);
            List<ChatCommand.Turn> updated = new ArrayList<>(history);
            updated.add(new ChatCommand.Turn("judgment_" + UUID.randomUUID(), response.context(),
                    response.citations().stream().map(ChatMessageResponse.Citation::entryId).distinct().toList(), response.decision()));
            if (updated.size() > 8) updated = new ArrayList<>(updated.subList(updated.size() - 8, updated.size()));
            state.conversations.remove(contextKey);
            state.conversations.put(contextKey, List.copyOf(updated));
            while (state.conversations.keySet().stream().filter(k->k.startsWith(scope+":")).count() > 4)
                state.conversations.remove(state.conversations.keySet().stream().filter(k->k.startsWith(scope+":")).findFirst().orElseThrow());
            session.setAttribute(ATTRIBUTE, state);
            return response.withConversationId(conversationId);
        }
    }

    public List<ChatMessage> history(String userId, String conversationId) {
        return messages == null ? List.of() : messages.findAllByUserIdAndConversationIdOrderByCreatedAtAsc(userId, conversationId).stream().filter(m->m.getDemoScope().equals(scopes==null?"REAL":scopes.current(userId))).toList();
    }
    public List<String> conversations(String userId) { return messages == null ? List.of() : messages.findAllByUserIdOrderByCreatedAtDesc(userId).stream().filter(m->m.getDemoScope().equals(scopes==null?"REAL":scopes.current(userId))).map(ChatMessage::getConversationId).distinct().sorted().toList(); }
    public void deleteHistory(String userId, String conversationId) { if (messages != null) messages.deleteAll(history(userId,conversationId)); }

    private static final class State {
        final String userId;
        final LinkedHashMap<String, List<ChatCommand.Turn>> conversations = new LinkedHashMap<>();
        State(String userId) { this.userId = userId; }
    }
}
