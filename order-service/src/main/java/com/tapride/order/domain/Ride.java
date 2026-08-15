package com.tapride.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Current-state read model for a ride. The source of truth for "what happened" is
 * the RideEvent log (event-sourcing-lite); this table is the materialized view
 * optimized for point lookups and the API layer, kept in sync by RideEventPublisher
 * within the same transaction as each event append.
 */
@Entity
@Table(name = "rides")
@Getter
@Setter
@NoArgsConstructor
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID riderId;

    @Column(nullable = true)
    private UUID driverId;

    @Column(nullable = false)
    private double pickupLat;

    @Column(nullable = false)
    private double pickupLng;

    @Column(nullable = false)
    private double dropoffLat;

    @Column(nullable = false)
    private double dropoffLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RideStatus status;

    @Column(precision = 10, scale = 2)
    private BigDecimal fare;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Optimistic locking - protects against concurrent saga steps racing on the same ride. */
    @Version
    private long version;

    public Ride(UUID riderId, double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        this.riderId = riderId;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
        this.status = RideStatus.REQUESTED;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyStatus(RideStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }
}
