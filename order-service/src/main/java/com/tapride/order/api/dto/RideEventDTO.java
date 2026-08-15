package com.tapride.order.api.dto;

import com.tapride.order.domain.RideEvent;
import com.tapride.order.domain.RideEventType;

import java.time.Instant;
import java.util.UUID;

public record RideEventDTO(
        UUID id,
        RideEventType eventType,
        String payload,
        String correlationId,
        Instant occurredAt
) {
    public static RideEventDTO from(RideEvent event) {
        return new RideEventDTO(event.getId(), event.getEventType(), event.getPayload(),
                event.getCorrelationId(), event.getOccurredAt());
    }
}
