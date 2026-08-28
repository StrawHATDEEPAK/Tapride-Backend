package com.tapride.order.api;

import com.tapride.order.api.dto.ChaosSettingsDTO;
import com.tapride.order.chaos.ChaosSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Same shape as payment-service/matching-service's ChaosController, but this
 * one controls a simulated failure on order-service's OWN read path
 * (GET /api/rides/{id}) rather than a downstream service call - see
 * ChaosSettings for why. Flip forceFailure on, hammer the endpoint with
 * several requests in a row, and watch the circuit breaker trip to OPEN
 * (visible once Day 5's Grafana/Actuator metrics are wired up, or by
 * observing responses switch from 503-after-retries to instant 503s once
 * the breaker opens and stops even trying).
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

    @PostMapping("/reset")
    public ResponseEntity<ChaosSettingsDTO> reset() {
        chaosSettings.setForceFailure(false);
        chaosSettings.setFailureRate(0.0);
        return ResponseEntity.ok(new ChaosSettingsDTO(chaosSettings.getFailureRate(), chaosSettings.isForceFailure()));
    }
}
