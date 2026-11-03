/** 참여자 꽃 색. 입장 순서대로 배정하며 방 안에서 중복되지 않는다. */
package com.dandimate.durooduroo.member.domain;

import java.util.Arrays;
import java.util.Collection;

public enum FlowerCode {
    AZALEA,
    FORSYTHIA,
    ROYAL_AZALEA,
    CANOLA,
    LILAC,
    FORGET_ME_NOT,
    TEAL,
    PURPLE;

    /** 아직 쓰이지 않은 꽃 중 순서가 가장 빠른 것. 정원(8명)이 차면 null. */
    public static FlowerCode firstAvailable(Collection<FlowerCode> used) {
        return Arrays.stream(values()).filter(code -> !used.contains(code)).findFirst().orElse(null);
    }
}
