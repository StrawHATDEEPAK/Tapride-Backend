package com.tapride.payment.api;

import com.tapride.payment.api.dto.PaymentResponseDTO;
import com.tapride.payment.domain.Payment;
import com.tapride.payment.domain.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Deliberately READ-ONLY. There is no POST /api/payments endpoint - payments
 * only ever get created in response to a Kafka event from order-service (see
 * RideEventConsumer). This API exists purely for inspection/debugging during
 * development and for the demo dashboard, not as a second way to trigger
 * payment authorization - that would create two competing entry points into
 * the saga, which is exactly the kind of inconsistency an orchestrated saga
 * is supposed to prevent.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/by-ride/{rideId}")
    public ResponseEntity<PaymentResponseDTO> getByRideId(@PathVariable UUID rideId) {
        Payment payment = paymentService.getByRideId(rideId);
        return ResponseEntity.ok(PaymentResponseDTO.from(payment));
    }
}
