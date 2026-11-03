package com.dandimate.durooduroo.room.dto;

import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomPlan;

/** 방 생성 전 플랜 선택 화면용 카탈로그. 가격은 방 1개 기준 원 단위. */
public record PlanView(RoomPlan planType, int price, String currency, String billingUnit,
                       boolean paymentRequired, boolean roomCreationAvailable, String unavailableReason,
                       int maxCapacity, int photosPerMember, int photosPerRoom,
                       int firstMaxSeconds, int otherMaxSeconds, long voiceLimitMs, int regenerationLimit) {
    public static PlanView from(RoomPlan plan) {
        boolean paid = plan.requiresPayment();
        // 결제 검증이 연결되기 전에는 유료 플랜의 방 생성을 지원하지 않는다.
        return new PlanView(plan, plan.priceKrw(), "KRW", "ROOM", paid, !paid,
                paid ? "PAYMENT_NOT_CONFIGURED" : null, Room.CAPACITY,
                plan.photosPerMember(), plan.photosPerRoom(), plan.firstSeconds(),
                plan.otherSeconds(), plan.voiceLimitMs(), plan.regenerations());
    }
}
