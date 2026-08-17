package com.tapride.matching.api;

import com.tapride.matching.api.dto.ChaosSettingsDTO;
import com.tapride.matching.chaos.ChaosSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Same shape as payment-service's ChaosController - see that class for full rationale. */
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
        chaosSettings.setFailureRate(0.10);
        return ResponseEntity.ok(new ChaosSettingsDTO(chaosSettings.getFailureRate(), chaosSettings.isForceFailure()));
    }
}
