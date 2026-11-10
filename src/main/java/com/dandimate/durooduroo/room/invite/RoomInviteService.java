/** 방마다 하나뿐인 고정 초대 링크를 암호화해 보관하고 다시 꺼내 준다. */
package com.dandimate.durooduroo.room.invite;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomInviteService {

    private final RoomRepository rooms;
    private final MemberRepository members;
    private final InviteTokenCipher cipher;

    public RoomInviteService(RoomRepository rooms, MemberRepository members, InviteTokenCipher cipher) {
        this.rooms = rooms;
        this.members = members;
        this.cipher = cipher;
    }

    public void retainCreated(Room room) {
        if (room.getEncryptedInviteToken() == null && room.getIssuedInviteToken() != null) {
            retain(room, room.getIssuedInviteToken());
        }
    }

    @Transactional
    public String get(String roomId, String authorization) {
        Room room = rooms.requireByIdForUpdate(roomId);
        members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "참여자만 초대 링크를 조회할 수 있습니다."));
        // 예전 방은 해시만 남아 원문을 되돌릴 수 없다. 기존 링크는 계속 유효하게 두고, 다시 꺼낼 수 있는 링크를 하나 만든다.
        if (room.getEncryptedInviteToken() == null) {
            String token = room.getIssuedInviteToken() != null ? room.getIssuedInviteToken() : Tokens.generate();
            retain(room, token);
        }
        return cipher.decrypt(room.getId(), room.getEncryptedInviteToken());
    }

    @Transactional
    public void remember(String token) {
        Room room = rooms.findByInviteTokenHashForUpdate(Tokens.hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "초대 링크가 없습니다."));
        if (room.getEncryptedInviteToken() == null) {
            retain(room, token);
        }
    }

    private void retain(Room room, String token) {
        room.retainInviteToken(cipher.encrypt(room.getId(), token), Tokens.hash(token));
    }
}
