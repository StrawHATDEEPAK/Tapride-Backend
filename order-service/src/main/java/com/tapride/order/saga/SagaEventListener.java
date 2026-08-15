package com.tapride.order.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tapride.order.domain.RideService;
import com.tapride.order.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The saga orchestrator's "inbox": listens for events published by payment-service
 * and matching-service, and drives the ride state machine forward (or triggers
 * compensation) in response. order-service is the orchestrator - it owns the
 * saga's control flow, while payment/matching-service are participants that only
 * know how to do their own step and report success/failure.
 *
 * Expected event contracts (published by services we build on Day 2/3):
 *   tapride.payment.events -> { type: "PAYMENT_AUTHORIZED" | "PAYMENT_FAILED" | "PAYMENT_REFUNDED", rideId, ... }
 *   tapride.matching.events -> { type: "DRIVER_MATCHED" | "DRIVER_MATCH_FAILED", rideId, driverId?, ... }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaEventListener {

    private final RideService rideService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "order-service-saga")
    public void onPaymentEvent(String rawMessage) {
        try {
            JsonNode node = objectMapper.readTree(rawMessage);
            String type = node.get("type").asText();
            UUID rideId = UUID.fromString(node.get("rideId").asText());
            String correlationId = node.hasNonNull("correlationId") ? node.get("correlationId").asText() : "unknown";

            switch (type) {
                case "PAYMENT_AUTHORIZED" -> rideService.handlePaymentAuthorized(rideId, correlationId);
                case "PAYMENT_FAILED" -> rideService.handlePaymentFailed(
                        rideId, node.path("reason").asText("unspecified"), correlationId);
                case "PAYMENT_REFUNDED" -> rideService.handlePaymentRefunded(rideId, correlationId);
                default -> log.warn("Unhandled payment event type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", rawMessage, e);
            // In production this would route to a dead-letter topic rather than swallow.
        }
    }

    @KafkaListener(topics = KafkaTopics.MATCHING_EVENTS, groupId = "order-service-saga")
    public void onMatchingEvent(String rawMessage) {
        try {
            JsonNode node = objectMapper.readTree(rawMessage);
            String type = node.get("type").asText();
            UUID rideId = UUID.fromString(node.get("rideId").asText());
            String correlationId = node.hasNonNull("correlationId") ? node.get("correlationId").asText() : "unknown";

            switch (type) {
                case "DRIVER_MATCHED" -> rideService.handleDriverMatched(
                        rideId, UUID.fromString(node.get("driverId").asText()), correlationId);
                case "DRIVER_MATCH_FAILED" -> rideService.handleMatchFailed(
                        rideId, node.path("reason").asText("unspecified"), correlationId);
                default -> log.warn("Unhandled matching event type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to process matching event: {}", rawMessage, e);
        }
    }
}
