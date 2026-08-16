package com.tapride.payment.events;

public final class KafkaTopics {
    /** order-service's topic - we CONSUME from here, listening for commands aimed at us. */
    public static final String RIDE_EVENTS = "tapride.ride.events";
    /** Our topic - we PUBLISH here; order-service's saga listener consumes it. */
    public static final String PAYMENT_EVENTS = "tapride.payment.events";

    private KafkaTopics() {
    }
}
