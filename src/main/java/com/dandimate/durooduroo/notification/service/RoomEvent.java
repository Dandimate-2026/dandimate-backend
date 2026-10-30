/** SSE로 보낼 방 이벤트. targetMemberId가 있으면 그 참여자 스트림에만 보낸다. 본문·사진 URL은 넣지 않는다 (명세 10장). */
package com.dandimate.durooduroo.notification.service;

import java.util.Map;

public record RoomEvent(String roomId, String name, Map<String, Object> data, String targetMemberId) {

    public static RoomEvent toRoom(String roomId, String name, Map<String, Object> data) {
        return new RoomEvent(roomId, name, data, null);
    }

    public static RoomEvent toMember(String roomId, String memberId, String name, Map<String, Object> data) {
        return new RoomEvent(roomId, name, data, memberId);
    }
}
