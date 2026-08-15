package com.tapride.order.events;

import com.tapride.order.domain.RideEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire format for events published to tapride.ride.events.
 * Kept intentionally flat/simple for now; a schema registry + Avro contract is
 * the natural upgrade path once a second consumer service depends on this shape.
 */
public record RideEventMessage(
        UUID eventId,
        UUID rideId,
        RideEventType eventType,
        String payloadJson,
        String correlationId,
        Instant occurredAt
) {
}
