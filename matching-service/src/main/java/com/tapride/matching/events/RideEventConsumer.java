package com.tapride.matching.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tapride.matching.domain.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Same "inbox" pattern as payment-service's RideEventConsumer: listens to
 * order-service's own event log, reacts only to the one event type that's a
 * command aimed at us, ignores everything else.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventConsumer {

    private final MatchingService matchingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.RIDE_EVENTS, groupId = "matching-service")
    public void onRideEvent(String rawMessage) {
        try {
            RideEventEnvelope envelope = objectMapper.readValue(rawMessage, RideEventEnvelope.class);

            if ("DRIVER_MATCH_REQUESTED".equals(envelope.eventType())) {
                UUID rideId = UUID.fromString(envelope.rideId());
                JsonNode payload = objectMapper.readTree(envelope.payloadJson());
                double pickupLat = payload.get("pickupLat").asDouble();
                double pickupLng = payload.get("pickupLng").asDouble();

                matchingService.requestMatch(rideId, pickupLat, pickupLng, envelope.correlationId());
            }
            // Every other ride event type (RIDE_REQUESTED, PAYMENT_AUTHORIZED,
            // etc.) isn't addressed to matching-service - silently ignored.
        } catch (Exception e) {
            log.error("Failed to process ride event: {}", rawMessage, e);
        }
    }
}
