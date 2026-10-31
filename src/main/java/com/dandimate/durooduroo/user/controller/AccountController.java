package com.dandimate.durooduroo.user.controller;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.user.repository.UserAccountRepository;
import com.dandimate.durooduroo.common.exception.BusinessException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.Map;
@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final UserAccountRepository users;
    private final MemberRepository members;
    private final RoomRepository rooms;
    public AccountController(UserAccountRepository users, MemberRepository members, RoomRepository rooms) {
        this.users=users; this.members=members; this.rooms=rooms;
    }
    @GetMapping("/auth/csrf")
    public ApiResponse<?> csrf(CsrfToken token) {
        return ApiResponse.success(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
    @GetMapping("/users/me")
    public ApiResponse<?> me() {
        var user = users.findById(CurrentUser.requireId()).orElseThrow(() -> new BusinessException("UNAUTHORIZED", "다시 로그인해 주세요."));
        return ApiResponse.success(Map.of("id", user.getId(), "createdAt", user.getCreatedAt()));
    }
    @GetMapping("/users/me/rooms")
    public ApiResponse<?> rooms() {
        var participation = members.findAllByUserId(CurrentUser.requireId());
        var byId = rooms.findAllById(participation.stream().map(m -> m.getRoomId()).toList()).stream()
            .collect(java.util.stream.Collectors.toMap(r -> r.getId(), r -> r));
        return ApiResponse.success(participation.stream().filter(m -> byId.containsKey(m.getRoomId())).map(m -> {
            var room = byId.get(m.getRoomId());
            return Map.of("roomId", room.getId(), "name", room.getTitle(), "status", room.getStatus(),
                "memberId", m.getId(), "nickname", m.getNickname(), "role", m.getRole());
        }).toList());
    }
}
