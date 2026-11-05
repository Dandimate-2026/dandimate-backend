package com.dandimate.durooduroo.payment;

public interface PaymentGateway {
    boolean configured();
    void approve(String paymentKey, String orderId, int amount);
}
