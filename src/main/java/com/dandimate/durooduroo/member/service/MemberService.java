/** 초대 토큰 입장, 정원·닉네임 중복 검사, 꽃 색 배정을 처리한다. */
package com.dandimate.durooduroo.member.service;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.domain.FlowerCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.dto.SpecJoinRoomRequest;
import com.dandimate.durooduroo.member.dto.SpecJoinRoomResponse;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final RoomRepository roomRepository;
    private final com.dandimate.durooduroo.notification.service.RoomEvents events;

    public MemberService(MemberRepository memberRepository, RoomRepository roomRepository, com.dandimate.durooduroo.notification.service.RoomEvents events) {
        this.events = events;
        this.memberRepository = memberRepository;
        this.roomRepository = roomRepository;
    }


    @Transactional
    public SpecJoinRoomResponse joinByInvite(String inviteToken, SpecJoinRoomRequest request) {
        Room room = roomRepository.findByInviteTokenHashForUpdate(com.dandimate.durooduroo.common.auth.Tokens.hash(inviteToken))
                .orElseThrow(() -> new BusinessException("INVITE_NOT_FOUND", "유효하지 않은 초대 토큰입니다."));
        String userId = com.dandimate.durooduroo.common.auth.CurrentUser.idOrNull();
        Member existing = userId == null ? null : memberRepository.findByRoomIdAndUserId(room.getId(), userId).orElse(null);
        if (existing != null && existing.isActive()) return SpecJoinRoomResponse.from(room, existing);
        if (memberRepository.countByRoomIdAndLeftAtIsNull(room.getId()) >= room.getCapacity()) {
            throw new BusinessException("ROOM_FULL", "방 정원이 가득 찼습니다.");
        }
        String nickname = request.nickname().trim();
        if (memberRepository.existsByRoomIdAndNicknameAndIdNot(room.getId(), nickname, existing == null ? "" : existing.getId())) {
            throw new BusinessException(com.dandimate.durooduroo.common.exception.ErrorCode.NICKNAME_TAKEN, "이미 사용 중인 이름이에요.",
                    java.util.Map.of("suggestions", suggestions(room.getId(), nickname)));
        }
        if (existing != null) {
            existing.rejoin(nickname, nextFlower(room.getId()));
            events.toRoom(room.getId(), "member.joined", java.util.Map.of("memberId", existing.getId()));
            return SpecJoinRoomResponse.from(room, existing);
        }
        Member joined = memberRepository.save(Member.join(room.getId(), nickname, nextFlower(room.getId()), userId));
        events.toRoom(room.getId(), "member.joined", java.util.Map.of("memberId", joined.getId()));
        return SpecJoinRoomResponse.from(room, joined);
    }

    /** 명세 1장: 닉네임이 겹치면 숫자를 붙인 추천 이름 2개(10자 이내, 이미 쓰는 이름 제외). 토큰을 잃은 사람도 기존 참여자로 복구하지 않고 새 이름을 쓴다. */
    private java.util.List<String> suggestions(String roomId, String nickname) {
        java.util.List<String> suggestions = new java.util.ArrayList<>();
        for (int n = 2; suggestions.size() < 2 && n < 100; n++) {
            String suffix = String.valueOf(n);
            String candidate = nickname.substring(0, Math.min(nickname.length(), 10 - suffix.length())) + suffix;
            if (!memberRepository.existsByRoomIdAndNickname(roomId, candidate)) suggestions.add(candidate);
        }
        return suggestions;
    }

    /** 방 행 잠금 안에서 정원 검사와 꽃 배정을 함께 처리한다. */
    private FlowerCode nextFlower(String roomId) {
        var used = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).stream().map(Member::getFlowerCode).toList();
        FlowerCode flower = FlowerCode.firstAvailable(used);
        if (flower == null) throw new BusinessException("ROOM_FULL", "방 정원이 가득 찼습니다.");
        return flower;
    }
}
