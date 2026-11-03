/** 명세서 "상태별 쓰기 허용" 표의 요청 종류와 허용 상태입니다. */
package com.dandimate.durooduroo.room.domain;

import java.util.EnumSet;
import java.util.Set;

public enum RoomAction {
    /** 사진 업로드·첫 한마디 (GROWING은 2차 FR-G5) */
    UPLOAD_OR_FIRST_ENTRY(EnumSet.of(RoomStatus.COLLECTING, RoomStatus.CIRCULATING)),
    /** 덧붙임·패스 */
    ADDON_OR_PASS(EnumSet.of(RoomStatus.CIRCULATING)),
    ASKBACK_ANSWER(EnumSet.of(RoomStatus.CIRCULATING)),
    EDIT_ENTRY(EnumSet.of(RoomStatus.COLLECTING, RoomStatus.CIRCULATING)),
    /** 원문·사진 삭제 */
    DELETE_CONTENT(EnumSet.of(RoomStatus.COLLECTING, RoomStatus.CIRCULATING, RoomStatus.GROWING)),
    REPLY(EnumSet.of(RoomStatus.GROWING));

    private final Set<RoomStatus> allowed;

    RoomAction(Set<RoomStatus> allowed) {
        this.allowed = allowed;
    }

    public boolean allows(RoomStatus status) {
        return allowed.contains(status);
    }
}
