package com.tapride.notification.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tapride.notification.dto.NormalizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The whole point of notification-service in one class: consume all 3 saga
 * event topics, normalize each into the same NormalizedEvent shape (see that
 * class for why), and broadcast to two WebSocket destinations per event - the
 * global firehose and a per-ride channel. Nothing is persisted; if no browser
 * is connected when an event arrives, it's simply gone (this is a live feed,
 * not an event history - GET /api/rides/{id}/events on order-service is
 * already the durable source of truth for that).
 *
 * Each of the 3 listener methods handles a DIFFERENT wire shape, because
 * order-service's own event log (tapride.ride.events) has a different
 * envelope than payment/matching-service's response topics do - see each
 * method's comment for the specific shape it's unwrapping.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventRelay {

    private static final String RIDE_EVENTS_TOPIC = "tapride.ride.events";
    private static final String PAYMENT_EVENTS_TOPIC = "tapride.payment.events";
    private static final String MATCHING_EVENTS_TOPIC = "tapride.matching.events";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * order-service's own event log doubles as its outbound commands - shape:
     * {eventId, rideId, eventType, payloadJson, correlationId, occurredAt}
     * payloadJson is itself a JSON STRING (double-encoded), matching the exact
     * pattern payment/matching-service's own consumers already unwrap.
     */
    @KafkaListener(topics = RIDE_EVENTS_TOPIC, groupId = "notification-service")
    public void onRideEvent(String rawMessage) {
        try {
            JsonNode envelope = objectMapper.readTree(rawMessage);
            String rideId = envelope.get("rideId").asText();
            String eventType = envelope.get("eventType").asText();
            String correlationId = envelope.path("correlationId").asText("unknown");
            String occurredAt = envelope.path("occurredAt").asText(Instant.now().toString());

            Map<String, Object> payload = objectMapper.readValue(
                    envelope.get("payloadJson").asText("{}"),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            broadcast(new NormalizedEvent("order-service", rideId, eventType, payload, correlationId, occurredAt));
        } catch (Exception e) {
            log.error("Failed to relay ride event: {}", rawMessage, e);
        }
    }

    /**
     * payment-service publishes flat maps, no envelope wrapper - shape:
     * {type, rideId, correlationId, ...eventSpecificFields}
     * "type" here is the field name for what we call eventType elsewhere -
     * payment/matching-service's own contract, not renamed for consistency
     * since each service's wire format is its own to define (see the root
     * README's "saga event contract" section for the full rationale).
     */
    @KafkaListener(topics = PAYMENT_EVENTS_TOPIC, groupId = "notification-service")
    public void onPaymentEvent(String rawMessage) {
        relayFlatEvent("payment-service", rawMessage);
    }

    /** Same flat shape as payment-service's topic - see onPaymentEvent. */
    @KafkaListener(topics = MATCHING_EVENTS_TOPIC, groupId = "notification-service")
    public void onMatchingEvent(String rawMessage) {
        relayFlatEvent("matching-service", rawMessage);
    }

    private void relayFlatEvent(String service, String rawMessage) {
        try {
            JsonNode node = objectMapper.readTree(rawMessage);
            String rideId = node.get("rideId").asText();
            String eventType = node.get("type").asText();
            String correlationId = node.path("correlationId").asText("unknown");

            // Everything except type/rideId/correlationId is event-specific
            // payload (driverId, amount, reason, lat/lng, etc.) - collect it
            // generically rather than hardcoding every possible field name.
            Map<String, Object> payload = new HashMap<>();
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                if (!field.equals("type") && !field.equals("rideId") && !field.equals("correlationId")) {
                    payload.put(field, objectMapper.convertValue(node.get(field), Object.class));
                }
            }

            broadcast(new NormalizedEvent(service, rideId, eventType, payload, correlationId, Instant.now().toString()));
        } catch (Exception e) {
            log.error("Failed to relay {} event: {}", service, rawMessage, e);
        }
    }

    private void broadcast(NormalizedEvent event) {
        messagingTemplate.convertAndSend("/topic/events", event);
        messagingTemplate.convertAndSend("/topic/rides/" + event.rideId(), event);
        log.debug("Relayed {} [{}] for ride {}", event.eventType(), event.service(), event.rideId());
    }
}
