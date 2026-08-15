package com.tapride.order.api;

import com.tapride.order.api.dto.CancelRideDTO;
import com.tapride.order.api.dto.RideEventDTO;
import com.tapride.order.api.dto.RideRequestDTO;
import com.tapride.order.api.dto.RideResponseDTO;
import com.tapride.order.domain.Ride;
import com.tapride.order.domain.RideService;
import com.tapride.order.repository.RideEventRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final RideService rideService;
    private final RideEventRepository rideEventRepository;

    @PostMapping
    public ResponseEntity<RideResponseDTO> requestRide(
            @Valid @RequestBody RideRequestDTO request,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationIdHeader) {

        String correlationId = correlationIdHeader != null ? correlationIdHeader : UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            Ride ride = rideService.requestRide(
                    request.riderId(), request.pickupLat(), request.pickupLng(),
                    request.dropoffLat(), request.dropoffLng(), correlationId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .body(RideResponseDTO.from(ride));
        } finally {
            MDC.remove("correlationId");
        }
    }

    @GetMapping("/{id}")
    @CircuitBreaker(name = "rideLookup", fallbackMethod = "rideLookupFallback")
    public ResponseEntity<RideResponseDTO> getRide(@PathVariable UUID id) {
        Ride ride = rideService.getOrThrow(id);
        return ResponseEntity.ok(RideResponseDTO.from(ride));
    }

    @SuppressWarnings("unused")
    private ResponseEntity<RideResponseDTO> rideLookupFallback(UUID id, Throwable t) {
        // Demonstrates graceful degradation: if the DB/dependency is unhealthy,
        // return 503 instead of a raw 500/timeout so callers can back off cleanly.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<RideEventDTO>> getRideEvents(@PathVariable UUID id) {
        List<RideEventDTO> events = rideEventRepository.findByRideIdOrderByOccurredAtAsc(id)
                .stream().map(RideEventDTO::from).toList();
        return ResponseEntity.ok(events);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelRide(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelRideDTO body,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationIdHeader) {

        String correlationId = correlationIdHeader != null ? correlationIdHeader : UUID.randomUUID().toString();
        String reason = (body != null && body.reason() != null) ? body.reason() : "user_requested";
        rideService.cancelRide(id, reason, correlationId);
        return ResponseEntity.noContent().build();
    }
}
