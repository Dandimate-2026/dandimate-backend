/** 방의 한마디들을 문단으로 생성하는 API입니다. */
package com.dandimate.durooduroo.ai.controller;
import com.dandimate.durooduroo.ai.service.AiParagraphService; import com.dandimate.durooduroo.common.api.ApiResponse; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/rooms/{roomId}/ai") public class AiController { private final AiParagraphService service; public AiController(AiParagraphService s){service=s;} @PostMapping("/generate") public ApiResponse<?> generate(@PathVariable String roomId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){service.requireHost(roomId,authorization,memberToken);return ApiResponse.success(service.generate(roomId));} }
