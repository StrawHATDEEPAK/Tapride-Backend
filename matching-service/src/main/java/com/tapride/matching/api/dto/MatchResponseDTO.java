package com.tapride.matching.api.dto;

import com.tapride.matching.domain.DriverMatch;
import com.tapride.matching.domain.MatchStatus;

import java.time.Instant;
import java.util.UUID;

public record MatchResponseDTO(
        UUID id,
        UUID rideId,
        UUID driverId,
        MatchStatus status,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng,
        double currentLat,
        double currentLng,
        Instant createdAt,
        Instant updatedAt
) {
    public static MatchResponseDTO from(DriverMatch match) {
        return new MatchResponseDTO(
                match.getId(), match.getRideId(), match.getDriverId(), match.getStatus(),
                match.getPickupLat(), match.getPickupLng(), match.getDropoffLat(), match.getDropoffLng(),
                match.getCurrentLat(), match.getCurrentLng(),
                match.getCreatedAt(), match.getUpdatedAt());
    }
}
