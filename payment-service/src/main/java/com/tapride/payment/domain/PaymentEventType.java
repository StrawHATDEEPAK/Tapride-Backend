package com.tapride.payment.domain;

public enum PaymentEventType {
    PAYMENT_AUTHORIZATION_REQUESTED,
    PAYMENT_AUTHORIZED,
    PAYMENT_FAILED,
    PAYMENT_REFUND_REQUESTED,
    PAYMENT_REFUNDED
}
