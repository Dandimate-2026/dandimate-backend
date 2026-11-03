/** 초대 토큰 입장 API의 응답 형식입니다. */
package com.dandimate.durooduroo.member.dto;

import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.dto.SpecCreateRoomResponse;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record SpecJoinRoomResponse(SpecCreateRoomResponse.RoomInfo room,
                                   SpecCreateRoomResponse.MemberInfo member,
                                   String memberAccessToken) {
    public static SpecJoinRoomResponse from(Room room, Member member) {
        var response = SpecCreateRoomResponse.from(room, member);
        return new SpecJoinRoomResponse(response.room(), response.member(), response.memberAccessToken());
    }
}
