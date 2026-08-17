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
            "ASSIGNED, EN_ROUTE",
            "ASSIGNED, CANCELLED",
            "EN_ROUTE, ARRIVED",
            "EN_ROUTE, CANCELLED",
    })
    void allows_legal_transitions(MatchStatus from, MatchStatus to) {
        assertDoesNotThrow(() -> stateMachine.assertTransitionAllowed(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "ARRIVED, CANCELLED",     // terminal state can't transition
            "CANCELLED, EN_ROUTE",    // terminal state can't transition
            "ASSIGNED, ARRIVED",      // can't skip straight to arrived
    })
    void rejects_illegal_transitions(MatchStatus from, MatchStatus to) {
        assertThrows(MatchStateMachine.IllegalStateTransitionException.class,
                () -> stateMachine.assertTransitionAllowed(from, to));
    }

    @Test
    void terminal_states_are_identified_correctly() {
        assert stateMachine.isTerminal(MatchStatus.ARRIVED);
        assert stateMachine.isTerminal(MatchStatus.CANCELLED);
        assert !stateMachine.isTerminal(MatchStatus.ASSIGNED);
        assert !stateMachine.isTerminal(MatchStatus.EN_ROUTE);
    }
}
