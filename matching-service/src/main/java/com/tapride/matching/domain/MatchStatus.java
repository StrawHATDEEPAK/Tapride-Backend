package com.tapride.matching.domain;

/**
 * Full driver journey for a ride: two legs (to pickup, then to dropoff),
 * matching the ride's actual lifecycle rather than stopping at pickup.
 */
public enum MatchStatus {
    ASSIGNED,             // driver picked, not yet moving
    EN_ROUTE_PICKUP,      // simulated location ticking toward pickup
    ARRIVED_PICKUP,       // reached pickup - triggers order-service's startRide()
    EN_ROUTE_DROPOFF,     // second leg - ticking toward dropoff
    COMPLETED,            // reached dropoff - triggers order-service's completeRide(); driver returns to available pool
    CANCELLED
}
