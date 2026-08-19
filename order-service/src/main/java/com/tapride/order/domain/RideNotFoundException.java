package com.tapride.order.domain;

import java.util.UUID;

/**
 * Dedicated "not found" signal for the ride domain - deliberately NOT
 * java.util.NoSuchElementException, which is meant for iterator/Optional.get()
 * exhaustion, not domain lookups. Using a generic JDK exception for this meant
 * any accidental, unrelated iterator misuse elsewhere in the codebase would be
 * silently caught by the same @ExceptionHandler and misreported as a 404.
 */
public class RideNotFoundException extends RuntimeException {
    public RideNotFoundException(UUID rideId) {
        super("Ride not found: " + rideId);
    }
}