package com.tapride.payment.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tapride.payment.domain.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * payment-service's "inbox": listens to order-service's tapride.ride.events
 * topic (NOT a dedicated "payment commands" topic - order-service publishes its
 * own state transitions, and we simply filter for the two event types that are
 * commands aimed at us). This keeps order-service's event log as the single
 * append-only history of "what order-service asked for and when", rather than
 * scattering command messages across multiple topics.
 *
 * We only react to two event types here; everything else on this topic
 * (RIDE_REQUESTED, RIDE_VALIDATED, DRIVER_MATCHED, etc.) is irrelevant to
 * payment-service and is silently ignored.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.RIDE_EVENTS, groupId = "payment-service")
    public void onRideEvent(String rawMessage) {
        try {
            // Outer envelope: {eventId, rideId, eventType, payloadJson, correlationId, occurredAt}
            RideEventEnvelope envelope = objectMapper.readValue(rawMessage, RideEventEnvelope.class);

            switch (envelope.eventType()) {
                case "PAYMENT_AUTHORIZATION_REQUESTED" -> handleAuthorizationRequested(envelope);
                case "PAYMENT_REFUND_REQUESTED" -> handleRefundRequested(envelope);
                default -> {
                    // Not addressed to us - every other ride event type is expected
                    // and silently skipped, not an error.
                }
            }
        } catch (Exception e) {
            log.error("Failed to process ride event: {}", rawMessage, e);
            // In production this would route to a dead-letter topic rather than
            // swallow the error - flagged here as a known scope trade-off.
        }
    }

    private void handleAuthorizationRequested(RideEventEnvelope envelope) throws Exception {
        UUID rideId = UUID.fromString(envelope.rideId());
        // payloadJson is itself a JSON string (order-service double-encodes its
        // event payload) - parse it to pull out the estimated fare it computed.
        JsonNode payload = objectMapper.readTree(envelope.payloadJson());
        BigDecimal amount = payload.has("estimatedFare")
                ? new BigDecimal(payload.get("estimatedFare").asText())
                : BigDecimal.ZERO;

        paymentService.authorize(rideId, amount, envelope.correlationId());
    }

    private void handleRefundRequested(RideEventEnvelope envelope) {
        UUID rideId = UUID.fromString(envelope.rideId());
        paymentService.refund(rideId, envelope.correlationId());
    }
}
