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
}
