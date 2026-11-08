package com.dandimate.durooduroo.scroll.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphVersionRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 재엮기 전후 문단을 비교할 수 있도록, 참여자에게 문단 버전 이력을 제공한다. */
@RestController
public class ParagraphVersionController {
    private final ParagraphRepository paragraphs;
    private final ParagraphVersionRepository versions;
    private final MemberRepository members;

    public ParagraphVersionController(ParagraphRepository paragraphs, ParagraphVersionRepository versions, MemberRepository members) {
        this.paragraphs = paragraphs;
        this.versions = versions;
        this.members = members;
    }

    @GetMapping("/api/v1/paragraphs/{paragraphId}/versions")
    public ApiResponse<?> list(@PathVariable String paragraphId, @RequestHeader(required = false) String authorization) {
        var paragraph = paragraphs.findById(paragraphId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "문단을 찾을 수 없습니다."));
        members.findByRoomIdAndMemberToken(paragraph.getRoomId(), MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 조회할 수 있습니다."));
        List<VersionView> result = versions.findAllByParagraphIdOrderByVersionAsc(paragraphId).stream()
                .map(v -> new VersionView(v.getVersion(), v.getBody(), v.getSources(), v.getReason(), v.getCreatedAt()))
                .toList();
        return ApiResponse.success(java.util.Map.of("versions", result));
    }

    public record VersionView(int version, String body, String sources, String reason, java.time.LocalDateTime createdAt) {}
}
