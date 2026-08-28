package com.tapride.matching.domain;

import java.util.UUID;

public class MatchNotFoundException extends RuntimeException {
    public MatchNotFoundException(UUID rideId) {
        super("No match found for ride: " + rideId);
    }
}