/** 참여자 데이터를 DB에 저장하고 닉네임 중복을 확인한다. */
package com.dandimate.durooduroo.member.repository;

import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, String> {

    Optional<Member> findByRoomIdAndUserId(String roomId, String userId);
    java.util.List<Member> findAllByUserId(String userId);

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
     * 세션의 계정 ID와 방 ID로 참가자를 찾는다. 다른 방이면 빈 값을 반환해 호출부가 403으로 거절한다.
     * 세션이 없는 내부 회귀 테스트만 기존 토큰 조회를 사용한다. HTTP 요청은 보안 필터에서 먼저 차단된다.
     */
    default Optional<Member> findByRoomIdAndMemberToken(String roomId, String rawToken) {
        String userId = com.dandimate.durooduroo.common.auth.CurrentUser.idOrNull();
        if (userId != null) return findByRoomIdAndUserId(roomId, userId);
        Member member = authenticate(rawToken);
        return member.getRoomId().equals(roomId) ? Optional.of(member) : Optional.empty();
    }
}
