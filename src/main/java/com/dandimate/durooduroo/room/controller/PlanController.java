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
    @GetMapping("/api/v1/plans")
    public ApiResponse<List<PlanView>> plans() {
        return ApiResponse.success(Arrays.stream(RoomPlan.values()).map(PlanView::from).toList());
    }
}
