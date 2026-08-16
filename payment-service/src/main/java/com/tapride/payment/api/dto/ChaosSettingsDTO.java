package com.tapride.payment.api.dto;

/**
 * Dual-purpose: used both as the response body for GET /api/chaos and as the
 * request body for PUT /api/chaos (fields the caller doesn't want to change
 * can simply be omitted/null - see ChaosController for how that's handled).
 */
public record ChaosSettingsDTO(
        Double failureRate,
        Boolean forceFailure
) {
}
