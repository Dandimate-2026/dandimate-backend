/** 명세서 기준 돌려보기 목록 API입니다. 잠금은 서버에서 적용한다. */
package com.dandimate.durooduroo.memory.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.memory.service.CirculationService;
import org.springframework.web.bind.annotation.*;

@RestController
public class CirculationController {

    private final CirculationService circulationService;

    public CirculationController(CirculationService circulationService) {
        this.circulationService = circulationService;
    }

    @GetMapping("/api/v1/rooms/{roomId}/circulation")
    public ApiResponse<CirculationService.Page> get(@PathVariable String roomId, @RequestHeader(required = false) String authorization,
                                                    @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer size) {
        return ApiResponse.success(circulationService.cards(roomId, authorization, cursor, size));
    }
}
