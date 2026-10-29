package com.dandimate.durooduroo.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class RoomTest {

    @Test
    void 엮는_중에는_쓰기_요청이_ROOM_WEAVING으로_거절된다() {
        Room room = Room.create("경주", "ABC123");
        room.startCirculating();
        room.startWeaving();

        assertThatThrownBy(() -> room.requireAllows(RoomAction.UPLOAD_OR_FIRST_ENTRY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_WEAVING);
        assertThatThrownBy(room::startWeaving)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_WEAVING);
    }

    @Test
    void 마감_시각_기본값은_생성_후_48시간이다() {
        Room room = Room.create("경주", "ABC123");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.COLLECTING);
        assertThat(room.getDeadlineAt()).isEqualTo(room.getCreatedAt().plusHours(48));
    }

    @Test
    void 상태별_쓰기_허용표를_따른다() {
        assertThat(RoomAction.ADDON_OR_PASS.allows(RoomStatus.COLLECTING)).isFalse();
        assertThat(RoomAction.ADDON_OR_PASS.allows(RoomStatus.CIRCULATING)).isTrue();
        assertThat(RoomAction.DELETE_CONTENT.allows(RoomStatus.GROWING)).isTrue();
        assertThat(RoomAction.DELETE_CONTENT.allows(RoomStatus.WEAVING)).isFalse();
        assertThat(RoomAction.REPLY.allows(RoomStatus.GROWING)).isTrue();
        assertThat(RoomAction.REPLY.allows(RoomStatus.CIRCULATING)).isFalse();
    }
}
