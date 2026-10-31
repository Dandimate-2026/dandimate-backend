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

    public MemberService(MemberRepository memberRepository, RoomRepository roomRepository) {
        this.memberRepository = memberRepository;
        this.roomRepository = roomRepository;
    }


    @Transactional
    public SpecJoinRoomResponse joinByInvite(String inviteToken, SpecJoinRoomRequest request) {
        Room room = roomRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new BusinessException("INVITE_NOT_FOUND", "유효하지 않은 초대 토큰입니다."));
        String userId = com.dandimate.durooduroo.common.auth.CurrentUser.idOrNull();
        if (userId != null) {
            var existing = memberRepository.findByRoomIdAndUserId(room.getId(), userId);
            if (existing.isPresent()) return SpecJoinRoomResponse.from(room, existing.get());
        }
        if (memberRepository.countByRoomId(room.getId()) >= Room.CAPACITY) {
            throw new BusinessException("ROOM_FULL", "방 정원이 가득 찼습니다.");
        }
        String nickname = request.nickname().trim();
        if (memberRepository.existsByRoomIdAndNickname(room.getId(), nickname)) {
            throw new BusinessException("DUPLICATE_NICKNAME", "같은 방에서 이미 사용 중인 닉네임입니다.");
        }
        return SpecJoinRoomResponse.from(room, memberRepository.save(Member.join(room.getId(), nickname, nextFlower(room.getId()), userId)));
    }

    /** 동시에 입장하면 같은 꽃이 골라질 수 있지만 uk_members_room_flower가 한 명만 통과시킨다. */
    private FlowerCode nextFlower(String roomId) {
        var used = memberRepository.findAllByRoomId(roomId).stream().map(Member::getFlowerCode).toList();
        FlowerCode flower = FlowerCode.firstAvailable(used);
        if (flower == null) throw new BusinessException("ROOM_FULL", "방 정원이 가득 찼습니다.");
        return flower;
    }
}
