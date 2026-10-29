/** OpenAPI 명세서 기준 방 생성 응답입니다. */
package com.dandimate.durooduroo.room.dto;

import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.room.domain.Room;
import java.time.LocalDateTime;

public record SpecCreateRoomResponse(RoomInfo room, MemberInfo member, String inviteToken, String memberAccessToken) {
    public record RoomInfo(String id, String name, String status, LocalDateTime deadlineAt) {}
    public record MemberInfo(String id, String nickname, String role, String flowerCode) {}

    public static SpecCreateRoomResponse from(Room room, Member member) {
        return new SpecCreateRoomResponse(
                new RoomInfo(room.getId(), room.getTitle(), room.getStatus().name(), room.getDeadlineAt()),
                new MemberInfo(member.getId(), member.getNickname(), member.getRole().name(), "AZALEA"),
                room.getInviteCode(), member.getMemberToken());
    }
}
