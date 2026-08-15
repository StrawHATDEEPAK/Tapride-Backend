package com.tapride.order.events;

/**
 * Topic naming convention: {domain}.{entity}.{tense}
 * All ride lifecycle events flow through one topic, partitioned by rideId, so that
 * every event for a given ride lands on the same partition and is consumed in order.
 */
public final class KafkaTopics {
    public static final String RIDE_EVENTS = "tapride.ride.events";
    public static final String PAYMENT_EVENTS = "tapride.payment.events";
    public static final String MATCHING_EVENTS = "tapride.matching.events";

    private KafkaTopics() {
    }
}
