package com.dandimate.durooduroo.chat.dto;

import com.dandimate.durooduroo.member.domain.Member;

/** 해당 방에서의 공개 프로필. 인증 토큰·이메일·카카오 식별자는 포함하지 않는다. */
public record ChatAuthorView(String userId, String memberId, String nickname, String role, String flowerCode) {
    public static ChatAuthorView from(Member member) {
        return new ChatAuthorView(member.getUserId(), member.getId(), member.getNickname(),
                member.getRole().name(), member.getFlowerCode().name());
    }
}
