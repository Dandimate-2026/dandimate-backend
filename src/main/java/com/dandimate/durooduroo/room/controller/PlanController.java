package com.dandimate.durooduroo.room.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.room.domain.RoomPlan;
import com.dandimate.durooduroo.room.dto.PlanView;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanController {
    private final com.dandimate.durooduroo.payment.PaymentGateway gateway;
    public PlanController(com.dandimate.durooduroo.payment.PaymentGateway gateway) { this.gateway = gateway; }
    @GetMapping("/api/v1/plans")
    public ApiResponse<List<PlanView>> plans() {
        return ApiResponse.success(Arrays.stream(RoomPlan.values()).map(p -> PlanView.from(p, gateway.configured())).toList());
    }
}
