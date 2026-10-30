/** 참여자 데이터를 DB에 저장하고 닉네임 중복을 확인한다. */
package com.dandimate.durooduroo.member.repository;

import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, String> {

    boolean existsByRoomIdAndNickname(String roomId, String nickname);

    long countByRoomId(String roomId);
    java.util.List<Member> findAllByRoomId(String roomId);

    Optional<Member> findByAccessTokenHash(String accessTokenHash);

    /** 토큰 원문으로 참여자를 찾는다. 어느 방에도 없는 토큰이면 401 INVALID_MEMBER_TOKEN. */
    default Member authenticate(String rawToken) {
        return findByAccessTokenHash(Tokens.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_MEMBER_TOKEN, "참여자 토큰이 올바르지 않습니다."));
    }

    /**
     * 토큰이 이 방의 참여자 것인지 확인한다. 토큰 자체가 무효면 401을 던지고,
     * 다른 방 참여자의 토큰이면 빈 값을 돌려줘 호출부가 403으로 거절하게 한다.
     */
    default Optional<Member> findByRoomIdAndMemberToken(String roomId, String rawToken) {
        Member member = authenticate(rawToken);
        return member.getRoomId().equals(roomId) ? Optional.of(member) : Optional.empty();
    }
}
