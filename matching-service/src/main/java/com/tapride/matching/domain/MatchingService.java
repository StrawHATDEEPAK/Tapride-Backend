package com.tapride.matching.domain;

import com.tapride.matching.chaos.ChaosSettings;
import com.tapride.matching.events.MatchEventPublisher;
import com.tapride.matching.repository.DriverMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * matching-service's saga participant logic - same shape as payment-service's
 * PaymentService: do our one step (find + assign a driver), report success or
 * failure, know nothing about what order-service does next.
 *
 * Search radius is fixed at 5km for this demo; a real system would expand the
 * radius progressively if nothing is found nearby rather than failing outright
 * on the first try - documented here as a known simplification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private static final double SEARCH_RADIUS_KM = 5.0;

    private final DriverMatchRepository driverMatchRepository;
    private final com.tapride.matching.repository.DriverRepository driverRepository;
    private final MatchStateMachine stateMachine;
    private final MatchEventPublisher eventPublisher;
    private final DriverLocationIndex driverLocationIndex;
    private final ChaosSettings chaosSettings;

    @Transactional
    public void requestMatch(UUID rideId, double pickupLat, double pickupLng,
                              double dropoffLat, double dropoffLng, String correlationId) {
        // Idempotency guard - same rationale as PaymentService.authorize: Kafka
        // is at-least-once delivery, a saga participant must tolerate redelivery.
        if (driverMatchRepository.findByRideId(rideId).isPresent()) {
            log.info("Match for ride {} already exists, ignoring duplicate request", rideId);
            return;
        }

        // Chaos gate checked BEFORE the Redis search, not after - this is
        // deliberately "no driver could be found" rather than "a driver was
        // found then something else went wrong", which keeps the failure
        // reason honest and simple for the demo.
        if (chaosSettings.shouldFail()) {
            eventPublisher.appendAndPublish(rideId, MatchEventType.DRIVER_MATCH_FAILED, correlationId,
                    Map.of("reason", "no_drivers_available_chaos_injected"));
            log.info("Match FAILED for ride {} (chaos-injected) [correlationId={}]", rideId, correlationId);
            return;
        }

                var nearestDriver = driverLocationIndex.findNearestAvailableDriver(pickupLat, pickupLng, SEARCH_RADIUS_KM);

        // No driver already near this pickup - rather than failing (the
        // fleet was only ever seeded around one fixed demo city, but real
        // bookings can now come from anywhere via search/geolocation), grow
        // the fleet on the spot. This keeps "no drivers available" as
        // something you only see via deliberate chaos injection, not as an
        // artifact of testing from a different city than the seed data.
        if (nearestDriver.isEmpty()) {
            spawnDriverNear(pickupLat, pickupLng);
            nearestDriver = driverLocationIndex.findNearestAvailableDriver(pickupLat, pickupLng, SEARCH_RADIUS_KM);
        }

        if (nearestDriver.isEmpty()) {
            eventPublisher.appendAndPublish(rideId, MatchEventType.DRIVER_MATCH_FAILED, correlationId,
                    Map.of("reason", "no_drivers_within_radius"));
            log.info("Match FAILED for ride {} - no drivers within {}km [correlationId={}]",
                    rideId, SEARCH_RADIUS_KM, correlationId);
            return;
        }

        UUID driverId = nearestDriver.get();
        Point driverStart = driverLocationIndex.getLocation(driverId)
                .orElse(new Point(pickupLng, pickupLat)); // fallback: shouldn't happen if seeded correctly

        // Reserve the driver immediately so a second concurrent match request
        // can't also grab them while we finish this transaction.
        driverLocationIndex.markBusy(driverId);

        DriverMatch match = new DriverMatch(rideId, driverId, pickupLat, pickupLng,
                dropoffLat, dropoffLng, driverStart.getY(), driverStart.getX());
        driverMatchRepository.save(match);

        eventPublisher.appendAndPublish(rideId, MatchEventType.DRIVER_MATCHED, correlationId,
                Map.of("driverId", driverId));
        log.info("Ride {} matched to driver {} [correlationId={}]", rideId, driverId, correlationId);
    }

        private static final java.util.List<String> SPAWN_NAMES = java.util.List.of(
            "Aditya", "Meera", "Sanjay", "Pooja", "Rakesh");
    private static final java.util.List<String> SPAWN_VEHICLES = java.util.List.of(
            "Hatchback", "Sedan", "SUV");

    /** Grows the fleet near a pickup point that had no nearby available driver. */
    private void spawnDriverNear(double lat, double lng) {
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        double offsetKm = 1.5; // close enough to arrive quickly, far enough to still "approach"
        double dLat = (rnd.nextDouble() - 0.5) * 2 * offsetKm / 111.0;
        double dLng = (rnd.nextDouble() - 0.5) * 2 * offsetKm / (111.0 * Math.cos(Math.toRadians(lat)));

        var driver = new Driver(java.util.UUID.randomUUID(),
                SPAWN_NAMES.get(rnd.nextInt(SPAWN_NAMES.size())),
                SPAWN_VEHICLES.get(rnd.nextInt(SPAWN_VEHICLES.size())));
        driverRepository.save(driver);
        driverLocationIndex.markAvailable(driver.getId(), lat + dLat, lng + dLng);
        log.info("Spawned driver {} near ({}, {}) - no existing driver was in range", driver.getId(), lat, lng);
    }

    @Transactional(readOnly = true)
    public DriverMatch getByRideId(UUID rideId) {
        return driverMatchRepository.findByRideId(rideId)
                .orElseThrow(() -> new MatchNotFoundException(rideId));
    }
}
