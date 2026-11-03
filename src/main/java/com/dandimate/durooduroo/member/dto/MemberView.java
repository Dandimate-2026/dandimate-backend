/** OpenAPI Member 스키마의 기본 필드입니다. 원문·사진·두루마리 응답에 작성자로 들어간다. */
package com.dandimate.durooduroo.member.dto;

import com.dandimate.durooduroo.member.domain.Member;

public record MemberView(String id, String nickname, String role, String flowerCode) {

    public static MemberView from(Member member) {
        return new MemberView(member.getId(), member.getNickname(), member.getRole().name(), member.getFlowerCode().name());
    }
}
