package com.dandimate.durooduroo.chat;

import com.dandimate.durooduroo.ai.chat.ChatCommand;
import com.dandimate.durooduroo.chat.dto.*;
import com.dandimate.durooduroo.chat.service.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatConversationTest {
    @Test void boundedContextIsIsolatedBySessionUserAndConversation() {
        ChatService chat = mock(ChatService.class);
        var count = new AtomicInteger();
        when(chat.answer(anyString(), anyString(), anyList())).thenAnswer(inv -> {
            List<ChatCommand.Turn> history = inv.getArgument(2);
            count.set(history.size());
            return new ChatMessageResponse("응답", List.of());
        });
        var service = new ChatConversationService(chat);
        var session = new MockHttpSession();
        var request = new ChatMessageRequest("질문", "conversation-one");
        for (int i = 0; i < 12; i++) service.answer("user-one", request, session);
        assertThat(count.get()).isEqualTo(8);
        service.answer("user-one", new ChatMessageRequest("새 질문", "conversation-two"), session);
        assertThat(count.get()).isZero();
        service.answer("user-one", request, new MockHttpSession());
        assertThat(count.get()).isZero();
        service.answer("user-two", request, session);
        assertThat(count.get()).isZero();
    }

    @Test void omittedIdAlsoGetsPersistentConversationAndOldConversationsAreEvicted() {
        ChatService chat = mock(ChatService.class);
        when(chat.answer(anyString(), anyString(), anyList())).thenReturn(new ChatMessageResponse("응답", List.of()));
        var service = new ChatConversationService(chat);
        var session = new MockHttpSession();
        var response=service.answer("user", new ChatMessageRequest("독립 질문"), session);
        assertThat(response.conversationId()).isNotBlank();
        verify(chat).answer("user", "독립 질문", List.of());
        for (int i = 0; i < 5; i++) service.answer("user", new ChatMessageRequest("질문", "conversation-" + i), session);
        service.answer("user", new ChatMessageRequest("다시 질문", "conversation-0"), session);
        verify(chat).answer("user", "다시 질문", List.of());
    }
}
