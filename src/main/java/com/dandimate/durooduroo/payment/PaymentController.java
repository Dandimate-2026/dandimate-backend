package com.dandimate.durooduroo.payment;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.room.domain.RoomPlan;
import com.dandimate.durooduroo.room.dto.SpecCreateRoomRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }
    public record UpgradeRequest(@NotNull RoomPlan planType) {}
    public record ConfirmRequest(@NotBlank @Size(max = 36) String orderId,
            @NotBlank @Size(max = 200) String paymentKey, @Positive int amount) {}

    public record CreditOrderRequest(@NotBlank @Pattern(regexp="light|basic|plenty") String packId) {}
    @PostMapping("/payments/credit-orders") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> credit(@Valid @RequestBody CreditOrderRequest request) {
        return ApiResponse.success(service.createCreditOrder(request.packId()));
    }
    @PostMapping("/payments/room-orders") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody SpecCreateRoomRequest request) {
        return ApiResponse.success(service.createRoomOrder(request));
    }
    @PostMapping("/rooms/{roomId}/plan-orders") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> upgrade(@PathVariable String roomId, @Valid @RequestBody UpgradeRequest request) {
        return ApiResponse.success(service.createUpgradeOrder(roomId, request.planType()));
    }
    @GetMapping("/payments/orders/{orderId}")
    public ApiResponse<?> order(@PathVariable String orderId) { return ApiResponse.success(service.get(orderId)); }
    @PostMapping("/payments/confirm")
    public ApiResponse<?> confirm(@Valid @RequestBody ConfirmRequest request) {
        return ApiResponse.success(service.confirm(request.orderId(), request.paymentKey(), request.amount()));
    }
}
