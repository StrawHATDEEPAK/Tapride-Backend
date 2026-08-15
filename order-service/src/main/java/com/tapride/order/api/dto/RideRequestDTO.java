package com.tapride.order.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RideRequestDTO(
        @NotNull UUID riderId,
        @NotNull Double pickupLat,
        @NotNull Double pickupLng,
        @NotNull Double dropoffLat,
        @NotNull Double dropoffLng
) {
}
