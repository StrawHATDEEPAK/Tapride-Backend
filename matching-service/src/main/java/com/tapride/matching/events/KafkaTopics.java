package com.tapride.matching.events;

public final class KafkaTopics {
    /** order-service's topic - we consume DRIVER_MATCH_REQUESTED from here. */
    public static final String RIDE_EVENTS = "tapride.ride.events";
    /** Our topic - DRIVER_MATCHED/DRIVER_MATCH_FAILED/DRIVER_LOCATION_UPDATED go here. */
    public static final String MATCHING_EVENTS = "tapride.matching.events";

    private KafkaTopics() {
    }
}
