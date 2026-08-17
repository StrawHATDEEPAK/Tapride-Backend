package com.tapride.matching.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per ride that successfully got a driver. currentLat/currentLng are
 * the SIMULATED position, ticked forward by DriverLocationSimulator on a
 * schedule - this is the groundwork for the Day 6 "live map" visualization:
 * the frontend will poll (or later, WebSocket-subscribe to) this position as
 * it moves from the driver's starting point toward the ride's pickup location.
 */
@Entity
@Table(name = "driver_matches")
@Getter
@Setter
@NoArgsConstructor
public class DriverMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID rideId;

    @Column(nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchStatus status;

    /** Where the ride needs the driver to go - copied from the match request, never changes. */
    @Column(nullable = false)
    private double pickupLat;
    @Column(nullable = false)
    private double pickupLng;

    /** Simulated live position - moves toward pickupLat/pickupLng on each simulator tick. */
    @Column(nullable = false)
    private double currentLat;
    @Column(nullable = false)
    private double currentLng;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public DriverMatch(UUID rideId, UUID driverId, double pickupLat, double pickupLng,
                        double driverStartLat, double driverStartLng) {
        this.rideId = rideId;
        this.driverId = driverId;
        this.status = MatchStatus.ASSIGNED;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.currentLat = driverStartLat;
        this.currentLng = driverStartLng;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updatePosition(double lat, double lng) {
        this.currentLat = lat;
        this.currentLng = lng;
        this.updatedAt = Instant.now();
    }

    public void applyStatus(MatchStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }
}
