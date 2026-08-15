package com.tapride.order.api.dto;

import com.tapride.order.domain.Ride;
import com.tapride.order.domain.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RideResponseDTO(
        UUID id,
        UUID riderId,
        UUID driverId,
        RideStatus status,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng,
        BigDecimal fare,
        Instant createdAt,
        Instant updatedAt
) {
    public static RideResponseDTO from(Ride ride) {
        return new RideResponseDTO(
                ride.getId(), ride.getRiderId(), ride.getDriverId(), ride.getStatus(),
                ride.getPickupLat(), ride.getPickupLng(), ride.getDropoffLat(), ride.getDropoffLng(),
                ride.getFare(), ride.getCreatedAt(), ride.getUpdatedAt());
    }
}
