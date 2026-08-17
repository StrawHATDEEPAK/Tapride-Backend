package com.tapride.matching.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tapride.matching.domain.MatchEvent;
import com.tapride.matching.domain.MatchEventType;
import com.tapride.matching.repository.MatchEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Third copy of the same transactional-outbox-simplified pattern used by
 * order-service's RideEventPublisher and payment-service's PaymentEventPublisher.
 * Deliberately not extracted into a shared library across services - see the
 * root README for why: each service's event log is that service's own concern,
 * and a shared "event publishing" dependency would be exactly the kind of
 * hidden coupling this architecture is trying to avoid. Some duplication here
 * is the intentional cost of true service independence.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MatchEventPublisher {

    private final MatchEventRepository matchEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void appendAndPublish(UUID rideId, MatchEventType type, String correlationId, Map<String, Object> extraFields) {
        String payloadJson = toJson(extraFields);
        MatchEvent event = new MatchEvent(rideId, type, payloadJson, correlationId);
        matchEventRepository.save(event);

        Map<String, Object> wireMessage = new LinkedHashMap<>();
        wireMessage.put("type", type.name());
        wireMessage.put("rideId", rideId.toString());
        wireMessage.put("correlationId", correlationId);
        wireMessage.putAll(extraFields);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishNow(rideId, type, wireMessage);
            }
        });
    }

    private void publishNow(UUID rideId, MatchEventType type, Map<String, Object> wireMessage) {
        kafkaTemplate.send(KafkaTopics.MATCHING_EVENTS, rideId.toString(), wireMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for ride {}: {}", type, rideId, ex.getMessage(), ex);
                    } else {
                        log.debug("Published {} for ride {}", type, rideId);
                    }
                });
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize event payload, storing empty object", e);
            return "{}";
        }
    }
}
