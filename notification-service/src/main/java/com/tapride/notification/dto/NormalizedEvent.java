package com.tapride.notification.dto;

import java.util.Map;

/**
 * All 3 backend services publish slightly different wire shapes (order-service
 * wraps its payload as a nested JSON string; payment/matching-service publish
 * flat maps). Rather than making the frontend understand 3 different formats,
 * this is the ONE consistent shape every event gets normalized into before
 * reaching a browser - "service" tells the frontend which part of the system
 * this came from, everything else is uniform.
 */
public record NormalizedEvent(
        String service,        // "order-service" | "payment-service" | "matching-service"
        String rideId,
        String eventType,
        Map<String, Object> payload,
        String correlationId,
        String occurredAt
) {
}
