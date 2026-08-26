package com.tapride.matching.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchStateMachineTest {

    private final MatchStateMachine stateMachine = new MatchStateMachine();

    @ParameterizedTest
    @CsvSource({
            "ASSIGNED, EN_ROUTE_PICKUP",
            "ASSIGNED, CANCELLED",
            "EN_ROUTE_PICKUP, ARRIVED_PICKUP",
            "EN_ROUTE_PICKUP, CANCELLED",
            "ARRIVED_PICKUP, EN_ROUTE_DROPOFF",
            "ARRIVED_PICKUP, CANCELLED",
            "EN_ROUTE_DROPOFF, COMPLETED",
            "EN_ROUTE_DROPOFF, CANCELLED",
    })
    void allows_legal_transitions(MatchStatus from, MatchStatus to) {
        assertDoesNotThrow(() -> stateMachine.assertTransitionAllowed(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "COMPLETED, CANCELLED",          // terminal state can't transition
            "CANCELLED, EN_ROUTE_PICKUP",     // terminal state can't transition
            "ASSIGNED, ARRIVED_PICKUP",       // can't skip straight to arrived
            "ASSIGNED, EN_ROUTE_DROPOFF",     // can't skip the pickup leg entirely
            "EN_ROUTE_PICKUP, EN_ROUTE_DROPOFF", // must pass through ARRIVED_PICKUP first
    })
    void rejects_illegal_transitions(MatchStatus from, MatchStatus to) {
        assertThrows(MatchStateMachine.IllegalStateTransitionException.class,
                () -> stateMachine.assertTransitionAllowed(from, to));
    }

    @Test
    void terminal_states_are_identified_correctly() {
        assert stateMachine.isTerminal(MatchStatus.COMPLETED);
        assert stateMachine.isTerminal(MatchStatus.CANCELLED);
        assert !stateMachine.isTerminal(MatchStatus.ASSIGNED);
        assert !stateMachine.isTerminal(MatchStatus.EN_ROUTE_PICKUP);
        assert !stateMachine.isTerminal(MatchStatus.ARRIVED_PICKUP);
        assert !stateMachine.isTerminal(MatchStatus.EN_ROUTE_DROPOFF);
    }
}
