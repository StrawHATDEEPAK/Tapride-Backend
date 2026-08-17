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
 * Ticks every matched driver's simulated position a step closer to the ride's
 * pickup point, on a fixed schedule. This is the groundwork for the eventual
 * "live map" visualization (deferred to end-of-project per the plan) - built
 * now, while matching-service's event schema is being designed, rather than
 * retrofitted later. No frontend consumes DRIVER_LOCATION_UPDATED yet; it's
 * simply published to Kafka and sits there, ready for notification-service or
 * the frontend to pick up whenever that's built.
 *
 * Movement model is deliberately simple - linear interpolation toward the
 * target at a fixed step size per tick, not real routing/road-following. This
 * is a simulation for demo purposes, not a routing engine.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverLocationSimulator {

    /** Fraction of the remaining distance covered per tick - higher = faster simulated arrival. */
    private static final double STEP_FRACTION = 0.15;
    /** Once this close (in degrees, roughly ~100m), consider the driver arrived. */
    private static final double ARRIVAL_THRESHOLD_DEG = 0.001;

    private final DriverMatchRepository driverMatchRepository;
    private final DriverLocationIndex driverLocationIndex;
    private final MatchEventPublisher eventPublisher;
    private final MatchStateMachine stateMachine;

    @Scheduled(fixedDelayString = "${matching.simulation.tick-interval-ms:4000}")
    @Transactional
    public void tick() {
        List<DriverMatch> active = driverMatchRepository.findByStatusIn(
                List.of(MatchStatus.ASSIGNED, MatchStatus.EN_ROUTE));

        for (DriverMatch match : active) {
            advance(match);
        }
    }

    private void advance(DriverMatch match) {
        double newLat = match.getCurrentLat() + (match.getPickupLat() - match.getCurrentLat()) * STEP_FRACTION;
        double newLng = match.getCurrentLng() + (match.getPickupLng() - match.getCurrentLng()) * STEP_FRACTION;

        boolean arrived = Math.abs(match.getPickupLat() - newLat) < ARRIVAL_THRESHOLD_DEG
                && Math.abs(match.getPickupLng() - newLng) < ARRIVAL_THRESHOLD_DEG;

        match.updatePosition(newLat, newLng);
        driverLocationIndex.updateLocation(match.getDriverId(), newLat, newLng);

        if (match.getStatus() == MatchStatus.ASSIGNED) {
            stateMachine.assertTransitionAllowed(MatchStatus.ASSIGNED, MatchStatus.EN_ROUTE);
            match.applyStatus(MatchStatus.EN_ROUTE);
        }

        eventPublisher.appendAndPublish(match.getRideId(), MatchEventType.DRIVER_LOCATION_UPDATED,
                "system", Map.of("driverId", match.getDriverId(), "lat", newLat, "lng", newLng));

        if (arrived) {
            stateMachine.assertTransitionAllowed(match.getStatus(), MatchStatus.ARRIVED);
            match.applyStatus(MatchStatus.ARRIVED);
            eventPublisher.appendAndPublish(match.getRideId(), MatchEventType.DRIVER_ARRIVED,
                    "system", Map.of("driverId", match.getDriverId()));
            log.info("Driver {} arrived at pickup for ride {}", match.getDriverId(), match.getRideId());
        }

        driverMatchRepository.save(match);
    }
}
