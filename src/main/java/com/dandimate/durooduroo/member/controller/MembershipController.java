package com.dandimate.durooduroo.member.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.member.service.MembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}")
public class MembershipController {
    private final MembershipService service;
    public MembershipController(MembershipService service) { this.service = service; }

    public record RenameRequest(@NotBlank @Size(max = 10) String nickname) {}
    public record PlanRequest(@NotNull MembershipService.PlanRequestReason reason) {}

    @PatchMapping("/members/me")
    public ApiResponse<?> rename(@PathVariable String roomId, @Valid @RequestBody RenameRequest request) {
        return ApiResponse.success(service.rename(roomId, request.nickname()));
    }

    @DeleteMapping("/members/me")
    public ResponseEntity<Void> leave(@PathVariable String roomId) {
        service.leave(roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plan-requests")
    public ResponseEntity<ApiResponse<?>> requestPlan(@PathVariable String roomId, @Valid @RequestBody PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.requestPlan(roomId, request.reason())));
    }
}
