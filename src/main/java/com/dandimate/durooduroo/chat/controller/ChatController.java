package com.dandimate.durooduroo.chat.controller;
import com.dandimate.durooduroo.chat.dto.*;
import com.dandimate.durooduroo.chat.service.ChatService;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/chat")
public class ChatController {
    private final ChatService chat;
    public ChatController(ChatService chat){this.chat=chat;}
    @PostMapping("/messages")
    public ApiResponse<ChatMessageResponse> message(@Valid @RequestBody ChatMessageRequest request) {
        return ApiResponse.success(chat.answer(CurrentUser.requireId(),request.message()));
    }
}
