package com.tapride.matching.simulation;

import com.tapride.matching.domain.*;
import com.tapride.matching.events.MatchEventPublisher;
import com.tapride.matching.repository.DriverMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Ticks every matched driver's simulated position a step closer to whichever
 * leg of the trip they're currently on - pickup first, then dropoff - on a
 * fixed schedule. Two-leg movement models the full ride lifecycle rather than
 * stopping once the driver reaches pickup:
 *
 *   ASSIGNED -> EN_ROUTE_PICKUP -> ARRIVED_PICKUP -> EN_ROUTE_DROPOFF -> COMPLETED
 *
 * DRIVER_ARRIVED (at pickup) and TRIP_COMPLETED (at dropoff) are consumed by
 * order-service's SagaEventListener to call startRide()/completeRide()
 * respectively - this is what actually drives the ride's own state machine
 * forward past DRIVER_MATCHED, all the way to COMPLETED.
 *
 * Movement model is deliberately simple - linear interpolation toward the
 * current target at a fixed step size per tick, not real routing/road-following.
 * This is a simulation for demo purposes, not a routing engine.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverLocationSimulator {

    /** Fraction of the remaining distance covered per tick - higher = faster simulated arrival. */
    private static final double STEP_FRACTION = 0.15;
    /** Once this close (in degrees, roughly ~100m), consider the driver arrived at the current target. */
    private static final double ARRIVAL_THRESHOLD_DEG = 0.001;

    private final DriverMatchRepository driverMatchRepository;
    private final DriverLocationIndex driverLocationIndex;
    private final MatchEventPublisher eventPublisher;
    private final MatchStateMachine stateMachine;

    @Scheduled(fixedDelayString = "${matching.simulation.tick-interval-ms:4000}")
    @Transactional
    public void tick() {
        List<DriverMatch> active = driverMatchRepository.findByStatusIn(
                List.of(MatchStatus.ASSIGNED, MatchStatus.EN_ROUTE_PICKUP, MatchStatus.EN_ROUTE_DROPOFF));

        for (DriverMatch match : active) {
            advance(match);
        }
    }

    private void advance(DriverMatch match) {
        // Which leg of the trip are we ticking toward right now?
        boolean headingToDropoff = match.getStatus() == MatchStatus.EN_ROUTE_DROPOFF;
        double targetLat = headingToDropoff ? match.getDropoffLat() : match.getPickupLat();
        double targetLng = headingToDropoff ? match.getDropoffLng() : match.getPickupLng();

        double newLat = match.getCurrentLat() + (targetLat - match.getCurrentLat()) * STEP_FRACTION;
        double newLng = match.getCurrentLng() + (targetLng - match.getCurrentLng()) * STEP_FRACTION;

        boolean reachedTarget = Math.abs(targetLat - newLat) < ARRIVAL_THRESHOLD_DEG
                && Math.abs(targetLng - newLng) < ARRIVAL_THRESHOLD_DEG;

        match.updatePosition(newLat, newLng);
        driverLocationIndex.updateLocation(match.getDriverId(), newLat, newLng);

        // First tick after being matched: kick off leg 1 (ASSIGNED -> EN_ROUTE_PICKUP).
        if (match.getStatus() == MatchStatus.ASSIGNED) {
            stateMachine.assertTransitionAllowed(MatchStatus.ASSIGNED, MatchStatus.EN_ROUTE_PICKUP);
            match.applyStatus(MatchStatus.EN_ROUTE_PICKUP);
        }

        eventPublisher.appendAndPublish(match.getRideId(), MatchEventType.DRIVER_LOCATION_UPDATED,
                "system", Map.of("driverId", match.getDriverId(), "lat", newLat, "lng", newLng));

        if (reachedTarget) {
            if (!headingToDropoff) {
                // Reached pickup: publish DRIVER_ARRIVED (order-service reacts by
                // starting the ride), then immediately begin leg 2 toward dropoff.
                stateMachine.assertTransitionAllowed(match.getStatus(), MatchStatus.ARRIVED_PICKUP);
                match.applyStatus(MatchStatus.ARRIVED_PICKUP);
                eventPublisher.appendAndPublish(match.getRideId(), MatchEventType.DRIVER_ARRIVED,
                        "system", Map.of("driverId", match.getDriverId()));
                log.info("Driver {} arrived at PICKUP for ride {}", match.getDriverId(), match.getRideId());

                stateMachine.assertTransitionAllowed(MatchStatus.ARRIVED_PICKUP, MatchStatus.EN_ROUTE_DROPOFF);
                match.applyStatus(MatchStatus.EN_ROUTE_DROPOFF);
            } else {
                // Reached dropoff: the trip is over. Publish TRIP_COMPLETED
                // (order-service reacts by completing the ride) and release the
                // driver back into the available pool so they can be matched again.
                stateMachine.assertTransitionAllowed(match.getStatus(), MatchStatus.COMPLETED);
                match.applyStatus(MatchStatus.COMPLETED);
                eventPublisher.appendAndPublish(match.getRideId(), MatchEventType.TRIP_COMPLETED,
                        "system", Map.of("driverId", match.getDriverId()));
                log.info("Driver {} completed trip (arrived at DROPOFF) for ride {}",
                        match.getDriverId(), match.getRideId());

                driverLocationIndex.markAvailable(match.getDriverId(), newLat, newLng);
            }
        }

        driverMatchRepository.save(match);
    }
}
