package com.tapride.matching.api.dto;

public record ChaosSettingsDTO(
        Double failureRate,
        Boolean forceFailure
) {
}
