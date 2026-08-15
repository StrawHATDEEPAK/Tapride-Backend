package com.tapride.order.domain;

/**
 * Every event type that can appear in a ride's event log. These map 1:1 to the
 * Kafka events published on the `tapride.ride.events` topic, so the log here
 * and the event stream other services see are always consistent.
 */
public enum RideEventType {
    RIDE_REQUESTED,
    RIDE_VALIDATED,
    RIDE_VALIDATION_FAILED,
    PAYMENT_AUTHORIZATION_REQUESTED,
    PAYMENT_AUTHORIZED,
    PAYMENT_FAILED,
    PAYMENT_REFUND_REQUESTED,
    PAYMENT_REFUNDED,
    DRIVER_MATCH_REQUESTED,
    DRIVER_MATCHED,
    DRIVER_MATCH_FAILED,
    RIDE_STARTED,
    RIDE_COMPLETED,
    RIDE_CANCELLED
}
