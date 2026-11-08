package com.dandimate.durooduroo.chat.controller;
import com.dandimate.durooduroo.chat.dto.*;
import com.dandimate.durooduroo.chat.service.ChatService;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import com.dandimate.durooduroo.chat.service.ChatConversationService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/chat")
public class ChatController {
    private final ChatConversationService conversations;
    public ChatController(ChatConversationService conversations){this.conversations=conversations;}
    @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.chat.quota.ChatQuotaService quota;
    @GetMapping("/quota")
    public ApiResponse<?> quota() { return ApiResponse.success(quota.get(CurrentUser.requireId())); }
    @PostMapping("/messages")
    public ApiResponse<ChatMessageResponse> message(@Valid @RequestBody ChatMessageRequest request, HttpSession session) {
        return ApiResponse.success(conversations.answer(CurrentUser.requireId(), request, session));
    }
    @GetMapping("/conversations")
    public ApiResponse<?> conversationIds() { return ApiResponse.success(java.util.Map.of("conversationIds", conversations.conversations(CurrentUser.requireId()))); }
    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<?> history(@PathVariable String conversationId) {
        var items = conversations.history(CurrentUser.requireId(), conversationId).stream()
                .map(m -> new MessageView(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt())).toList();
        return ApiResponse.success(java.util.Map.of("messages", items));
    }
    @DeleteMapping("/conversations/{conversationId}")
    public org.springframework.http.ResponseEntity<Void> deleteHistory(@PathVariable String conversationId) {
        conversations.deleteHistory(CurrentUser.requireId(), conversationId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
    public record MessageView(String id, String role, String content, java.time.LocalDateTime createdAt) {}
}
