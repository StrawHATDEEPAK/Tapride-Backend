package com.tapride.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RideStateMachineTest {

    private final RideStateMachine stateMachine = new RideStateMachine();

    @ParameterizedTest
    @CsvSource({
            "REQUESTED, VALIDATED",
            "VALIDATED, PAYMENT_PENDING",
            "PAYMENT_PENDING, PAYMENT_AUTHORIZED",
            "PAYMENT_AUTHORIZED, DRIVER_MATCHING",
            "DRIVER_MATCHING, DRIVER_MATCHED",
            "DRIVER_MATCHED, IN_PROGRESS",
            "IN_PROGRESS, COMPLETED",
            "MATCH_FAILED, PAYMENT_REFUNDING",
            "PAYMENT_REFUNDING, CANCELLED",
    })
    void allows_legal_transitions(RideStatus from, RideStatus to) {
        assertDoesNotThrow(() -> stateMachine.assertTransitionAllowed(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "REQUESTED, IN_PROGRESS",       // can't skip straight to in-progress
            "COMPLETED, CANCELLED",         // terminal state can't transition further
            "CANCELLED, REQUESTED",         // terminal state can't transition further
            "PAYMENT_PENDING, DRIVER_MATCHING", // can't skip payment authorization
            "VALIDATED, IN_PROGRESS",       // can't skip payment and matching entirely
    })
    void rejects_illegal_transitions(RideStatus from, RideStatus to) {
        assertThrows(RideStateMachine.IllegalStateTransitionException.class,
                () -> stateMachine.assertTransitionAllowed(from, to));
    }

    @Test
    void terminal_states_have_no_outgoing_transitions() {
        assertThrows(RideStateMachine.IllegalStateTransitionException.class,
                () -> stateMachine.assertTransitionAllowed(RideStatus.COMPLETED, RideStatus.CANCELLED));
        assertThrows(RideStateMachine.IllegalStateTransitionException.class,
                () -> stateMachine.assertTransitionAllowed(RideStatus.CANCELLED, RideStatus.COMPLETED));
    }

    @Test
    void isTerminal_correctly_identifies_terminal_states() {
        assert stateMachine.isTerminal(RideStatus.COMPLETED);
        assert stateMachine.isTerminal(RideStatus.CANCELLED);
        assert stateMachine.isTerminal(RideStatus.VALIDATION_FAILED);
        assert !stateMachine.isTerminal(RideStatus.REQUESTED);
        assert !stateMachine.isTerminal(RideStatus.IN_PROGRESS);
    }
}
