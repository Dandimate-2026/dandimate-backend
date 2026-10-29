/** 참여자 데이터를 DB에 저장하고 닉네임 중복을 확인한다. */
package com.dandimate.durooduroo.member.repository;

import com.dandimate.durooduroo.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, String> {

    boolean existsByRoomIdAndNickname(String roomId, String nickname);

    long countByRoomId(String roomId);
    java.util.List<Member> findAllByRoomId(String roomId);

    java.util.Optional<com.dandimate.durooduroo.member.domain.Member> findByRoomIdAndMemberToken(
            String roomId, String memberToken);
}
