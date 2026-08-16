package com.tapride.payment.events;

/**
 * Shape of the messages order-service publishes to tapride.ride.events.
 * Deliberately a LOCAL copy, not a shared library dependency on order-service's
 * RideEventMessage class - each service owns its understanding of the events it
 * consumes. If order-service changes its internal representation, this only
 * breaks if the wire contract (this shape) changes, not on every internal refactor.
 */
public record RideEventEnvelope(
        String eventId,
        String rideId,
        String eventType,
        String payloadJson,
        String correlationId,
        String occurredAt
) {
}
