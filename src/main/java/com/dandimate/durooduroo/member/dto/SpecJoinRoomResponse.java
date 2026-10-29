/** 초대 토큰 입장 API의 응답 형식입니다. */
package com.dandimate.durooduroo.member.dto;

import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.dto.SpecCreateRoomResponse;

public record SpecJoinRoomResponse(SpecCreateRoomResponse.RoomInfo room,
                                   SpecCreateRoomResponse.MemberInfo member,
                                   String memberAccessToken) {
    public static SpecJoinRoomResponse from(Room room, Member member) {
        var response = SpecCreateRoomResponse.from(room, member);
        return new SpecJoinRoomResponse(response.room(), response.member(), response.memberAccessToken());
    }
}
