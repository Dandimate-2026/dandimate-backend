package com.dandimate.durooduroo.room.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RoomHomeTodayTest {

    private static LocalDateTime serverTime(String instant) {
        return OffsetDateTime.parse(instant).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    @Test
    void 오늘은_한국_날짜_기준으로_자정에_바뀐다() {
        LocalDate today = LocalDate.of(2026, 11, 5);
        assertThat(RoomHomeService.isOn(serverTime("2026-11-05T00:10:00+09:00"), today)).isTrue();
        assertThat(RoomHomeService.isOn(serverTime("2026-11-05T23:50:00+09:00"), today)).isTrue();
        assertThat(RoomHomeService.isOn(serverTime("2026-11-04T23:50:00+09:00"), today)).isFalse();
    }
}
