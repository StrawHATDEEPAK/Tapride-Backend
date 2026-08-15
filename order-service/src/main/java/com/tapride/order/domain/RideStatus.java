package com.tapride.order.domain;

/**
 * Full lifecycle of a ride, including saga compensation (failure) states.
 *
 * Happy path:
 *   REQUESTED -> VALIDATED -> PAYMENT_AUTHORIZED -> DRIVER_MATCHED -> IN_PROGRESS -> COMPLETED
 *
 * Compensation paths (saga rollback):
 *   VALIDATED -> VALIDATION_FAILED (terminal)
 *   PAYMENT_AUTHORIZED (attempt) -> PAYMENT_FAILED -> CANCELLED (terminal)
 *   DRIVER_MATCHED (attempt) -> MATCH_FAILED -> PAYMENT_REFUNDING -> CANCELLED (terminal)
 *
 * Any active state can transition to CANCELLED via explicit user/operator cancellation,
 * which triggers compensating actions for whatever has already succeeded.
 */
public enum RideStatus {
    REQUESTED,
    VALIDATED,
    VALIDATION_FAILED,
    PAYMENT_PENDING,
    PAYMENT_AUTHORIZED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDING,
    DRIVER_MATCHING,
    DRIVER_MATCHED,
    MATCH_FAILED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
