package com.tapride.order.domain;

import com.tapride.order.events.RideEventPublisher;
import com.tapride.order.repository.RideRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Application service for the ride lifecycle. Every state change goes through
 * here so that: (1) the state machine is always consulted before mutating status,
 * (2) the Ride row and its RideEvent are written in the same transaction, and
 * (3) the Kafka event only fires after that transaction commits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RideService {

    private final RideRepository rideRepository;
    private final RideStateMachine stateMachine;
    private final RideEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final FareEstimator fareEstimator;

    @Transactional
    public Ride requestRide(UUID riderId, double pickupLat, double pickupLng,
                             double dropoffLat, double dropoffLng, String correlationId) {
        Ride ride = new Ride(riderId, pickupLat, pickupLng, dropoffLat, dropoffLng);
        ride = rideRepository.save(ride);

        eventPublisher.appendAndPublish(ride.getId(), RideEventType.RIDE_REQUESTED,
                toJson(Map.of("riderId", riderId, "pickupLat", pickupLat, "pickupLng", pickupLng,
                        "dropoffLat", dropoffLat, "dropoffLng", dropoffLng)),
                correlationId);

        // Simple synchronous validation for v0; a dedicated validation step could
        // become its own async saga stage later (e.g. fraud checks, rider standing).
        boolean valid = isValidRoute(pickupLat, pickupLng, dropoffLat, dropoffLng);
        if (valid) {
            var estimatedFare = fareEstimator.estimate(pickupLat, pickupLng, dropoffLat, dropoffLng);
            ride.setFare(estimatedFare);
            rideRepository.save(ride);

            transition(ride, RideStatus.VALIDATED, RideEventType.RIDE_VALIDATED, correlationId, Map.of());
            // Carries the estimated fare so payment-service has an amount to authorize
            // against without needing to know anything about ride geography itself.
            transition(ride, RideStatus.PAYMENT_PENDING, RideEventType.PAYMENT_AUTHORIZATION_REQUESTED,
                    correlationId, Map.of("rideId", ride.getId(), "estimatedFare", estimatedFare));
        } else {
            transition(ride, RideStatus.VALIDATION_FAILED, RideEventType.RIDE_VALIDATION_FAILED,
                    correlationId, Map.of("reason", "pickup and dropoff must differ"));
        }

        return ride;
    }

    @Transactional
    public void handlePaymentAuthorized(UUID rideId, String correlationId) {
        Ride ride = getOrThrow(rideId);
        transition(ride, RideStatus.PAYMENT_AUTHORIZED, RideEventType.PAYMENT_AUTHORIZED, correlationId, Map.of());
        // Immediately kick off the next saga stage: driver matching.
        transition(ride, RideStatus.DRIVER_MATCHING, RideEventType.DRIVER_MATCH_REQUESTED,
                correlationId, Map.of("rideId", rideId));
    }

    @Transactional
    public void handlePaymentFailed(UUID rideId, String reason, String correlationId) {
        Ride ride = getOrThrow(rideId);
        transition(ride, RideStatus.PAYMENT_FAILED, RideEventType.PAYMENT_FAILED,
                correlationId, Map.of("reason", reason));
        transition(ride, RideStatus.CANCELLED, RideEventType.RIDE_CANCELLED,
                correlationId, Map.of("reason", "payment_failed: " + reason));
    }

    @Transactional
    public void handleDriverMatched(UUID rideId, UUID driverId, String correlationId) {
        Ride ride = getOrThrow(rideId);
        ride.setDriverId(driverId);
        transition(ride, RideStatus.DRIVER_MATCHED, RideEventType.DRIVER_MATCHED,
                correlationId, Map.of("driverId", driverId));
    }

    @Transactional
    public void handleMatchFailed(UUID rideId, String reason, String correlationId) {
        Ride ride = getOrThrow(rideId);
        // Compensating action: driver matching failed after payment succeeded,
        // so we must refund before cancelling. This is the saga's rollback arm.
        transition(ride, RideStatus.MATCH_FAILED, RideEventType.DRIVER_MATCH_FAILED,
                correlationId, Map.of("reason", reason));
        transition(ride, RideStatus.PAYMENT_REFUNDING, RideEventType.PAYMENT_REFUND_REQUESTED,
                correlationId, Map.of("rideId", rideId));
    }

    @Transactional
    public void handlePaymentRefunded(UUID rideId, String correlationId) {
        Ride ride = getOrThrow(rideId);
        transition(ride, RideStatus.CANCELLED, RideEventType.RIDE_CANCELLED,
                correlationId, Map.of("reason", "match_failed_refunded"));
    }

    @Transactional
    public void startRide(UUID rideId, String correlationId) {
        Ride ride = getOrThrow(rideId);
        transition(ride, RideStatus.IN_PROGRESS, RideEventType.RIDE_STARTED, correlationId, Map.of());
    }

    @Transactional
    public void completeRide(UUID rideId, String correlationId) {
        Ride ride = getOrThrow(rideId);
        transition(ride, RideStatus.COMPLETED, RideEventType.RIDE_COMPLETED, correlationId, Map.of());
    }

    @Transactional
    public void cancelRide(UUID rideId, String reason, String correlationId) {
        Ride ride = getOrThrow(rideId);
        if (stateMachine.isTerminal(ride.getStatus())) {
            throw new IllegalStateException("Ride %s is already in terminal state %s".formatted(rideId, ride.getStatus()));
        }
        transition(ride, RideStatus.CANCELLED, RideEventType.RIDE_CANCELLED,
                correlationId, Map.of("reason", reason));
    }

    @Transactional(readOnly = true)
    public Ride getOrThrow(UUID rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new NoSuchElementException("Ride not found: " + rideId));
    }

    private void transition(Ride ride, RideStatus to, RideEventType eventType,
                             String correlationId, Map<String, Object> payload) {
        stateMachine.assertTransitionAllowed(ride.getStatus(), to);
        ride.applyStatus(to);
        rideRepository.save(ride);
        eventPublisher.appendAndPublish(ride.getId(), eventType, toJson(payload), correlationId);
        log.info("Ride {} transitioned -> {} [correlationId={}]", ride.getId(), to, correlationId);
    }

    private boolean isValidRoute(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        return !(pickupLat == dropoffLat && pickupLng == dropoffLng);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize event payload, storing empty object", e);
            return "{}";
        }
    }
}
