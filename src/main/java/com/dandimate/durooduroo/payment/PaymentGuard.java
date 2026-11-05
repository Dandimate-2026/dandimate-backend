package com.dandimate.durooduroo.payment;

import com.dandimate.durooduroo.common.exception.*;

public final class PaymentGuard {
    private PaymentGuard() {}
    /** Caller must first lock the room, matching payment reservation lock order. */
    public static void requireIdle(PaymentOrderRepository orders, String roomId) {
        if (orders.existsByRoomIdAndStatus(roomId, "PROCESSING"))
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS, "결제 확인 중입니다. 같은 주문으로 결제 상태를 확인한 뒤 다시 시도해 주세요.");
    }
}
