package com.tapride.matching.api;

import com.tapride.matching.api.dto.MatchResponseDTO;
import com.tapride.matching.domain.DriverMatch;
import com.tapride.matching.domain.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only, same rationale as PaymentController: matches only ever get
 * created in reaction to a Kafka event (see RideEventConsumer), never via a
 * direct POST here. This is purely for inspection - watching currentLat/
 * currentLng move on repeated calls is a manual preview of what the eventual
 * live map will show automatically.
 */
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchingService matchingService;

    @GetMapping("/by-ride/{rideId}")
    public ResponseEntity<MatchResponseDTO> getByRideId(@PathVariable UUID rideId) {
        DriverMatch match = matchingService.getByRideId(rideId);
        return ResponseEntity.ok(MatchResponseDTO.from(match));
    }
}
