package com.tapride.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tapride.payment.domain.PaymentEvent;
import com.tapride.payment.domain.PaymentEventType;
import com.tapride.payment.repository.PaymentEventRepository;
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
 * Same transactional-outbox-simplified pattern as order-service's RideEventPublisher:
 * append to the local event log inside the caller's transaction, publish to Kafka
 * only after that transaction commits. The outgoing wire format is a flat JSON
 * object (type/rideId/correlationId/...) matching exactly what order-service's
 * SagaEventListener expects - see order-service's SagaEventListener javadoc for
 * the full contract this fulfils.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final PaymentEventRepository paymentEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void appendAndPublish(UUID rideId, PaymentEventType type, String correlationId, Map<String, Object> extraFields) {
        String payloadJson = toJson(extraFields);
        PaymentEvent event = new PaymentEvent(rideId, type, payloadJson, correlationId);
        paymentEventRepository.save(event);

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

    private void publishNow(UUID rideId, PaymentEventType type, Map<String, Object> wireMessage) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, rideId.toString(), wireMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for ride {}: {}", type, rideId, ex.getMessage(), ex);
                    } else {
                        log.info("Published {} for ride {}", type, rideId);
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
