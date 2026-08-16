package com.tapride.payment.api;

import com.tapride.payment.api.dto.ChaosSettingsDTO;
import com.tapride.payment.chaos.ChaosSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Live control panel for chaos injection. This is what the frontend's
 * "chaos button" (Day 6) calls, and what you'd flip mid-demo to show:
 *   1. order-service's saga compensation path (PAYMENT_FAILED -> ride CANCELLED)
 *   2. Resilience4j's circuit breaker opening once failures spike (Day 4)
 *   3. Grafana's saga success/failure rate panel reacting in real time (Day 5)
 *
 * Deliberately in-memory only (see ChaosSettings) - this is a demo control
 * surface, not a persisted configuration, and resets on service restart.
 */
@RestController
@RequestMapping("/api/chaos")
@RequiredArgsConstructor
public class ChaosController {

    private final ChaosSettings chaosSettings;

    @GetMapping
    public ResponseEntity<ChaosSettingsDTO> get() {
        return ResponseEntity.ok(new ChaosSettingsDTO(chaosSettings.getFailureRate(), chaosSettings.isForceFailure()));
    }

    /**
     * Partial update - either field can be omitted to leave it unchanged.
     * e.g. {"forceFailure": true} alone flips on guaranteed failures for a demo
     * moment without touching the baseline random failure rate.
     */
    @PutMapping
    public ResponseEntity<ChaosSettingsDTO> update(@RequestBody ChaosSettingsDTO request) {
        if (request.failureRate() != null) {
            chaosSettings.setFailureRate(request.failureRate());
        }
        if (request.forceFailure() != null) {
            chaosSettings.setForceFailure(request.forceFailure());
        }
        return ResponseEntity.ok(new ChaosSettingsDTO(chaosSettings.getFailureRate(), chaosSettings.isForceFailure()));
    }

    /** Convenience reset back to the configured default - handy after a demo. */
    @PostMapping("/reset")
    public ResponseEntity<ChaosSettingsDTO> reset() {
        chaosSettings.setForceFailure(false);
        chaosSettings.setFailureRate(0.15);
        return ResponseEntity.ok(new ChaosSettingsDTO(chaosSettings.getFailureRate(), chaosSettings.isForceFailure()));
    }
}
