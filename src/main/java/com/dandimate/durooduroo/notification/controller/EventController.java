/** 명세서 기준 SSE 일회용 토큰과 이벤트 스트림 API입니다. */
package com.dandimate.durooduroo.notification.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.notification.service.RoomEventHub;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class EventController {

    private static final long STREAM_TOKEN_TTL_SECONDS = 60;

    private final MemberRepository members;
    private final RoomRepository rooms;
    private final RoomEventHub hub;
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    public EventController(MemberRepository members, RoomEventHub hub, RoomRepository rooms) {
        this.members = members;
        this.hub = hub;
        this.rooms = rooms;
    }

    /** EventSource는 헤더를 못 보내므로 60초 일회용 토큰을 담은 eventsUrl을 발급한다. 만료된 토큰은 발급 시 정리한다. */
    @PostMapping("/api/v1/rooms/{roomId}/event-token")
    public ResponseEntity<ApiResponse<?>> token(@PathVariable String roomId,
                                                @RequestHeader(required = false) String authorization) {
        var me = members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "참여자만 이벤트를 구독할 수 있습니다."));
        Instant now = Instant.now();
        tokens.values().removeIf(token -> token.expiresAt.isBefore(now));
        String value = Tokens.generate();
        Instant expiresAt = now.plusSeconds(STREAM_TOKEN_TTL_SECONDS);
        tokens.put(value, new Token(roomId, me.getId(), expiresAt));
        String eventsUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/rooms/{roomId}/events")
                .queryParam("streamToken", value)
                .buildAndExpand(roomId)
                .toUriString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("eventsUrl", eventsUrl, "expiresAt", expiresAt)));
    }

    @Transactional
    @GetMapping(value = "/api/v1/rooms/{roomId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String roomId, @RequestParam String streamToken) {
        Token token = tokens.remove(streamToken);
        if (token == null || !token.roomId.equals(roomId) || token.expiresAt.isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_MEMBER_TOKEN, "유효하지 않거나 만료된 스트림 토큰입니다.");
        }
        rooms.requireByIdForUpdate(roomId);
        if (members.findById(token.memberId).filter(Member::isActive).isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방에서 나간 참가자입니다.");
        }
        SseEmitter emitter = hub.subscribe(roomId, token.memberId);
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("roomId", roomId)));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private record Token(String roomId, String memberId, Instant expiresAt) {
    }
}
