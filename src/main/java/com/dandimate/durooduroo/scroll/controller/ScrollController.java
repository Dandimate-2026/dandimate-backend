/** 참여자 전용 두루마리 조회 API입니다. */
package com.dandimate.durooduroo.scroll.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.scroll.dto.ScrollView;
import com.dandimate.durooduroo.scroll.service.ScrollService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ScrollController {

    private final ScrollService scrollService;

    public ScrollController(ScrollService scrollService) {
        this.scrollService = scrollService;
    }

    @GetMapping("/api/v1/rooms/{roomId}/scroll")
    public ApiResponse<ScrollView> get(@PathVariable String roomId, @RequestHeader(required = false) String authorization) {
        return ApiResponse.success(scrollService.current(roomId, authorization));
    }
}
