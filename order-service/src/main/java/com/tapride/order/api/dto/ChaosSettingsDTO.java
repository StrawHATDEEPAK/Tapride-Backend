package com.tapride.order.api.dto;

public record ChaosSettingsDTO(
        Double failureRate,
        Boolean forceFailure
) {
}
