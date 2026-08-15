package com.tapride.order.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Guards every state transition a ride can make. Keeping this as an explicit,
 * declarative table (rather than scattering `if` checks through services) means:
 *   1. Illegal transitions fail fast with a clear error instead of corrupting state
 *   2. The full lifecycle graph is readable in one place
 *   3. Adding a new state/transition is a one-line change here, not a hunt through the codebase
 */
@Component
public class RideStateMachine {

    private static final Map<RideStatus, Set<RideStatus>> TRANSITIONS = new EnumMap<>(RideStatus.class);

    static {
        TRANSITIONS.put(RideStatus.REQUESTED, EnumSet.of(RideStatus.VALIDATED, RideStatus.VALIDATION_FAILED));
        TRANSITIONS.put(RideStatus.VALIDATED, EnumSet.of(RideStatus.PAYMENT_PENDING, RideStatus.CANCELLED));
        TRANSITIONS.put(RideStatus.VALIDATION_FAILED, EnumSet.noneOf(RideStatus.class)); // terminal

        TRANSITIONS.put(RideStatus.PAYMENT_PENDING, EnumSet.of(RideStatus.PAYMENT_AUTHORIZED, RideStatus.PAYMENT_FAILED));
        TRANSITIONS.put(RideStatus.PAYMENT_AUTHORIZED, EnumSet.of(RideStatus.DRIVER_MATCHING, RideStatus.PAYMENT_REFUNDING));
        TRANSITIONS.put(RideStatus.PAYMENT_FAILED, EnumSet.of(RideStatus.CANCELLED));

        TRANSITIONS.put(RideStatus.DRIVER_MATCHING, EnumSet.of(RideStatus.DRIVER_MATCHED, RideStatus.MATCH_FAILED));
        TRANSITIONS.put(RideStatus.DRIVER_MATCHED, EnumSet.of(RideStatus.IN_PROGRESS, RideStatus.CANCELLED));
        TRANSITIONS.put(RideStatus.MATCH_FAILED, EnumSet.of(RideStatus.PAYMENT_REFUNDING));

        TRANSITIONS.put(RideStatus.PAYMENT_REFUNDING, EnumSet.of(RideStatus.CANCELLED));

        TRANSITIONS.put(RideStatus.IN_PROGRESS, EnumSet.of(RideStatus.COMPLETED, RideStatus.CANCELLED));
        TRANSITIONS.put(RideStatus.COMPLETED, EnumSet.noneOf(RideStatus.class));   // terminal
        TRANSITIONS.put(RideStatus.CANCELLED, EnumSet.noneOf(RideStatus.class));   // terminal
    }

    /**
     * @throws IllegalStateTransitionException if the transition isn't allowed from the current state
     */
    public void assertTransitionAllowed(RideStatus from, RideStatus to) {
        Set<RideStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }

    public boolean isTerminal(RideStatus status) {
        return TRANSITIONS.getOrDefault(status, Set.of()).isEmpty();
    }

    public static class IllegalStateTransitionException extends RuntimeException {
        public IllegalStateTransitionException(RideStatus from, RideStatus to) {
            super("Illegal ride state transition: %s -> %s".formatted(from, to));
        }
    }
}
