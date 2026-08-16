package com.tapride.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentStateMachineTest {

    private final PaymentStateMachine stateMachine = new PaymentStateMachine();

    @ParameterizedTest
    @CsvSource({
            "PENDING, AUTHORIZED",
            "PENDING, FAILED",
            "AUTHORIZED, REFUNDING",
            "REFUNDING, REFUNDED",
    })
    void allows_legal_transitions(PaymentStatus from, PaymentStatus to) {
        assertDoesNotThrow(() -> stateMachine.assertTransitionAllowed(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "FAILED, AUTHORIZED",     // terminal state can't transition
            "REFUNDED, REFUNDING",    // terminal state can't transition
            "PENDING, REFUNDING",     // can't refund something never authorized
            "AUTHORIZED, FAILED",     // can't fail something already authorized - only refund
    })
    void rejects_illegal_transitions(PaymentStatus from, PaymentStatus to) {
        assertThrows(PaymentStateMachine.IllegalStateTransitionException.class,
                () -> stateMachine.assertTransitionAllowed(from, to));
    }

    @Test
    void terminal_states_are_identified_correctly() {
        assert stateMachine.isTerminal(PaymentStatus.FAILED);
        assert stateMachine.isTerminal(PaymentStatus.REFUNDED);
        assert !stateMachine.isTerminal(PaymentStatus.PENDING);
        assert !stateMachine.isTerminal(PaymentStatus.AUTHORIZED);
    }
}
