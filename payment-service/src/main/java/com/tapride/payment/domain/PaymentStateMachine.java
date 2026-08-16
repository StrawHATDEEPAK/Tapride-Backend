package com.tapride.payment.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Same declarative-transition-table pattern as order-service's RideStateMachine,
 * scaled down to payment's simpler lifecycle. Kept as an explicit class (rather
 * than inlined if/else) deliberately - consistency of pattern across services
 * matters more here than the line count saved by skipping it.
 */
@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        TRANSITIONS.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED));
        TRANSITIONS.put(PaymentStatus.AUTHORIZED, EnumSet.of(PaymentStatus.REFUNDING));
        TRANSITIONS.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));      // terminal
        TRANSITIONS.put(PaymentStatus.REFUNDING, EnumSet.of(PaymentStatus.REFUNDED));
        TRANSITIONS.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));    // terminal
    }

    public void assertTransitionAllowed(PaymentStatus from, PaymentStatus to) {
        Set<PaymentStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }

    public boolean isTerminal(PaymentStatus status) {
        return TRANSITIONS.getOrDefault(status, Set.of()).isEmpty();
    }

    public static class IllegalStateTransitionException extends RuntimeException {
        public IllegalStateTransitionException(PaymentStatus from, PaymentStatus to) {
            super("Illegal payment state transition: %s -> %s".formatted(from, to));
        }
    }
}
