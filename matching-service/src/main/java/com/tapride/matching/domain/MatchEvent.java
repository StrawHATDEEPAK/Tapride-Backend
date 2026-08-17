package com.tapride.matching.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_events")
@Getter
@NoArgsConstructor
public class MatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID rideId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchEventType eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 64)
    private String correlationId;

    @Column(nullable = false)
    private Instant occurredAt;

    public MatchEvent(UUID rideId, MatchEventType eventType, String payload, String correlationId) {
        this.rideId = rideId;
        this.eventType = eventType;
        this.payload = payload;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }
}
