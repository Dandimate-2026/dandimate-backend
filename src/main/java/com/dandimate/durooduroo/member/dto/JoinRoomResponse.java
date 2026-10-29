/** 참여자 입장 API가 반환하는 식별 정보 형식이다. */
package com.dandimate.durooduroo.member.dto;

import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.domain.MemberRole;
import java.time.LocalDateTime;

public record JoinRoomResponse(
        String memberId,
        String roomId,
        String nickname,
        String memberToken,
        MemberRole role,
        LocalDateTime joinedAt
) {

    public static JoinRoomResponse from(Member member) {
        return new JoinRoomResponse(
                member.getId(),
                member.getRoomId(),
                member.getNickname(),
                member.getIssuedAccessToken(),
                member.getRole(),
                member.getCreatedAt()
        );
    }
}
