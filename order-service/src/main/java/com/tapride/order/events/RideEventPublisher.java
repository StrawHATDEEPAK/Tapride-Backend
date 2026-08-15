package com.tapride.order.events;

import com.tapride.order.domain.RideEvent;
import com.tapride.order.domain.RideEventType;
import com.tapride.order.repository.RideEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Appends an event to the local event log (source of truth) inside the caller's
 * DB transaction, then publishes to Kafka only AFTER that transaction commits
 * successfully (via TransactionalEventListener). This avoids the classic
 * dual-write problem: we never publish an event for a DB write that got rolled back.
 *
 * This is the "transactional outbox, simplified" pattern - a full outbox table +
 * relay process is the textbook-correct version for exactly-once delivery guarantees;
 * this simpler version is documented here as a known trade-off for project scope.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventPublisher {

    private final RideEventRepository rideEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void appendAndPublish(UUID rideId, RideEventType type, String payloadJson, String correlationId) {
        RideEvent event = new RideEvent(rideId, type, payloadJson, correlationId);
        rideEventRepository.save(event);

        RideEventMessage message = new RideEventMessage(
                event.getId(), rideId, type, payloadJson, correlationId, event.getOccurredAt());

        // Deferred until commit - see class javadoc.
        pendingPublish(message);
    }

    private void pendingPublish(RideEventMessage message) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publishNow(message);
                    }
                }
        );
    }

    private void publishNow(RideEventMessage message) {
        kafkaTemplate.send(KafkaTopics.RIDE_EVENTS, message.rideId().toString(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ride event {} for ride {}: {}",
                                message.eventType(), message.rideId(), ex.getMessage(), ex);
                    } else {
                        log.info("Published {} for ride {} [correlationId={}]",
                                message.eventType(), message.rideId(), message.correlationId());
                    }
                });
    }
}
