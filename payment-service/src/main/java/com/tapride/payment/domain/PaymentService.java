package com.tapride.payment.domain;

import com.tapride.payment.chaos.ChaosSettings;
import com.tapride.payment.events.PaymentEventPublisher;
import com.tapride.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * payment-service's application service. This is a SAGA PARTICIPANT, not an
 * orchestrator: it only knows how to do its own step (authorize / refund) and
 * report success or failure back to order-service via Kafka. It has no idea
 * what happens next in the overall ride lifecycle - that decision belongs
 * entirely to order-service's RideStateMachine. This separation of concerns
 * (orchestrator owns the "what happens next" logic; participants own their own
 * step + honest reporting of outcome) is the core idea behind orchestrated sagas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateMachine stateMachine;
    private final PaymentEventPublisher eventPublisher;
    private final ChaosSettings chaosSettings;

    /**
     * Called when order-service asks us to authorize payment for a ride.
     * "Authorization" here is entirely mocked - no real payment gateway - but the
     * PENDING -> AUTHORIZED/FAILED transition and the chaos-driven failure
     * injection are real, which is what actually matters for demoing the saga.
     */
    @Transactional
    public void authorize(UUID rideId, BigDecimal amount, String correlationId) {
        // Idempotency guard: if order-service (or Kafka, on redelivery) sends the
        // same authorization request twice, don't create a second Payment row or
        // fire a second event - just no-op. Saga participants MUST be idempotent,
        // since "at least once" delivery is the norm for Kafka consumers.
        if (paymentRepository.findByRideId(rideId).isPresent()) {
            log.info("Payment for ride {} already exists, ignoring duplicate authorization request", rideId);
            return;
        }

        Payment payment = new Payment(rideId, amount);
        payment = paymentRepository.save(payment);

        // The actual "mock payment gateway" call - just a chaos-controlled coin flip.
        // This is the switch the Day 4 resilience demo flips live to show the saga's
        // compensation path (and Resilience4j's circuit breaker) kicking in.
        boolean fail = chaosSettings.shouldFail();

        if (fail) {
            stateMachine.assertTransitionAllowed(payment.getStatus(), PaymentStatus.FAILED);
            payment.setFailureReason("mock_gateway_declined");
            payment.applyStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            eventPublisher.appendAndPublish(rideId, PaymentEventType.PAYMENT_FAILED, correlationId,
                    Map.of("reason", "mock_gateway_declined", "amount", amount));
            log.info("Payment FAILED for ride {} (chaos-injected) [correlationId={}]", rideId, correlationId);
        } else {
            stateMachine.assertTransitionAllowed(payment.getStatus(), PaymentStatus.AUTHORIZED);
            payment.applyStatus(PaymentStatus.AUTHORIZED);
            paymentRepository.save(payment);

            eventPublisher.appendAndPublish(rideId, PaymentEventType.PAYMENT_AUTHORIZED, correlationId,
                    Map.of("amount", amount));
            log.info("Payment AUTHORIZED for ride {}, amount={} [correlationId={}]", rideId, amount, correlationId);
        }
    }

    /**
     * Called when order-service's saga is rolling back (e.g. driver matching
     * failed after payment succeeded) and needs the charge reversed. Always
     * succeeds in this mock - a real gateway integration would have its own
     * failure modes here too, but refund failure handling is out of scope for
     * this project's demo.
     */
    @Transactional
    public void refund(UUID rideId, String correlationId) {
        Payment payment = paymentRepository.findByRideId(rideId)
                .orElseThrow(() -> new NoSuchElementException("No payment found for ride: " + rideId));

        // Only a payment that actually succeeded can be refunded; if it already
        // failed there's nothing to reverse. Let the state machine enforce that
        // rather than checking manually here.
        stateMachine.assertTransitionAllowed(payment.getStatus(), PaymentStatus.REFUNDING);
        payment.applyStatus(PaymentStatus.REFUNDING);
        paymentRepository.save(payment);

        stateMachine.assertTransitionAllowed(payment.getStatus(), PaymentStatus.REFUNDED);
        payment.applyStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        eventPublisher.appendAndPublish(rideId, PaymentEventType.PAYMENT_REFUNDED, correlationId, Map.of());
        log.info("Payment REFUNDED for ride {} [correlationId={}]", rideId, correlationId);
    }

    @Transactional(readOnly = true)
    public Payment getByRideId(UUID rideId) {
        return paymentRepository.findByRideId(rideId)
                .orElseThrow(() -> new NoSuchElementException("No payment found for ride: " + rideId));
    }
}
