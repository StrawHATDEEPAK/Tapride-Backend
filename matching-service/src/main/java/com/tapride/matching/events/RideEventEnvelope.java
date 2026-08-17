package com.tapride.matching.events;

/** Local copy of order-service's wire format - see payment-service's identical
 * class for the full rationale on why this isn't a shared dependency. */
public record RideEventEnvelope(
        String eventId,
        String rideId,
        String eventType,
        String payloadJson,
        String correlationId,
        String occurredAt
) {
}
