package com.tapride.matching.domain;

/**
 * Tracks the DRIVER's journey for a ride - a narrower, more physical lifecycle
 * than order-service's RideStatus. order-service owns "is this ride still
 * happening"; matching-service owns "where is the driver right now". The two
 * are related but deliberately not the same state machine - matching-service
 * has no opinion on payment or the ride's business rules, only on assignment
 * and physical position.
 */
public enum MatchStatus {
    ASSIGNED,       // driver picked, not yet moving (initial tick hasn't run)
    EN_ROUTE,       // simulated location is ticking toward the pickup point
    ARRIVED,        // simulated driver has reached the pickup point - terminal for matching-service's purposes
    CANCELLED       // match failed or the ride was cancelled mid-match
}
