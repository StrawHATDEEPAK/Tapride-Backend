package com.tapride.matching.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class MatchStateMachine {

    private static final Map<MatchStatus, Set<MatchStatus>> TRANSITIONS = new EnumMap<>(MatchStatus.class);

    static {
        TRANSITIONS.put(MatchStatus.ASSIGNED, EnumSet.of(MatchStatus.EN_ROUTE, MatchStatus.CANCELLED));
        TRANSITIONS.put(MatchStatus.EN_ROUTE, EnumSet.of(MatchStatus.ARRIVED, MatchStatus.CANCELLED));
        TRANSITIONS.put(MatchStatus.ARRIVED, EnumSet.noneOf(MatchStatus.class));      // terminal
        TRANSITIONS.put(MatchStatus.CANCELLED, EnumSet.noneOf(MatchStatus.class));    // terminal
    }

    public void assertTransitionAllowed(MatchStatus from, MatchStatus to) {
        Set<MatchStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }

    public boolean isTerminal(MatchStatus status) {
        return TRANSITIONS.getOrDefault(status, Set.of()).isEmpty();
    }

    public static class IllegalStateTransitionException extends RuntimeException {
        public IllegalStateTransitionException(MatchStatus from, MatchStatus to) {
            super("Illegal match state transition: %s -> %s".formatted(from, to));
        }
    }
}
