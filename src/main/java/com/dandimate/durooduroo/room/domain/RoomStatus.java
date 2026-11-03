/** 방의 진행 상태를 정의한다: 모으는 중 → 돌려보는 중 → 엮는 중 → 완성·자라는 중. */
package com.dandimate.durooduroo.room.domain;

public enum RoomStatus {
    COLLECTING,
    CIRCULATING,
    WEAVING,
    GROWING
}
