package com.tapride.order.api;

import com.tapride.order.api.dto.CancelRideDTO;
import com.tapride.order.api.dto.RideEventDTO;
import com.tapride.order.api.dto.RideRequestDTO;
import com.tapride.order.api.dto.RideResponseDTO;
import com.tapride.order.chaos.ChaosSettings;
import com.tapride.order.domain.Ride;
import com.tapride.order.domain.RideService;
import com.tapride.order.repository.RideEventRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.tapride.order.domain.RideNotFoundException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final RideService rideService;
    private final RideEventRepository rideEventRepository;
    private final ChaosSettings chaosSettings;

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

    /**
     * Two resilience layers stacked here, in the order they're meant to be read:
     *   1. @Retry fires first - up to 3 attempts, 200ms apart, ONLY for genuine
     *      failures (not a legit "not found" - see ignore-exceptions in application.yml)
     *   2. @CircuitBreaker watches the outcome of those attempts - if failures
     *      keep piling up across many DIFFERENT requests (not just retries of
     *      one request), it trips OPEN and short-circuits straight to the
     *      fallback for ~10s, giving a struggling dependency room to recover
     *      instead of being hammered by an unbroken stream of retries.
     *
     * chaosSettings.maybeFail() is what makes this demonstrable - see
     * ChaosSettings for why order-service needed its own chaos hook when the
     * other two services already had one for their real external dependency.
     */
    @GetMapping("/{id}")
    @Retry(name = "rideLookup")
    @CircuitBreaker(name = "rideLookup", fallbackMethod = "rideLookupFallback")
    public ResponseEntity<RideResponseDTO> getRide(@PathVariable UUID id) {
        chaosSettings.maybeFail();
        Ride ride = rideService.getOrThrow(id);
        return ResponseEntity.ok(RideResponseDTO.from(ride));
    }

       @SuppressWarnings("unused")
    private ResponseEntity<RideResponseDTO> rideLookupFallback(UUID id, Throwable t) throws Throwable {
        // IMPORTANT: Resilience4j's fallbackMethod fires for EVERY exception the
        // guarded method throws - "ignore-exceptions" in application.yml only
        // controls whether an exception counts toward the circuit breaker's
        // failure-rate calculation, it does NOT stop the fallback from being
        // invoked. Without this explicit check, a legitimate 404 would get
        // silently swallowed into a fake 503 here. Rethrowing lets it propagate
        // normally to ApiExceptionHandler, which turns it into a real 404.
         if (t instanceof com.tapride.order.domain.RideNotFoundException) {
            throw t;
        }
        // Genuine infra/dependency trouble: degrade gracefully instead of a raw 500/timeout.
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
