package com.tapride.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only event log entry - the actual source of truth for a ride's history.
 * The `rides` table is a materialized read view derived from this log.
 * `payload` holds a JSON snapshot of whatever data the event carried, so the
 * full history can be replayed to reconstruct state at any point in time.
 */
@Entity
@Table(name = "ride_events")
@Getter
@NoArgsConstructor
public class RideEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID rideId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RideEventType eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    /** Correlation ID ties this event to a distributed trace + logs across all services. */
    @Column(nullable = false, length = 64)
    private String correlationId;

    @Column(nullable = false)
    private Instant occurredAt;

    public RideEvent(UUID rideId, RideEventType eventType, String payload, String correlationId) {
        this.rideId = rideId;
        this.eventType = eventType;
        this.payload = payload;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }
}
