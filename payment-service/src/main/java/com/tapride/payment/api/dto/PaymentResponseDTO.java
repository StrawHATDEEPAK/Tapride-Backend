package com.tapride.payment.api.dto;

import com.tapride.payment.domain.Payment;
import com.tapride.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponseDTO(
        UUID id,
        UUID rideId,
        BigDecimal amount,
        PaymentStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponseDTO from(Payment payment) {
        return new PaymentResponseDTO(
                payment.getId(), payment.getRideId(), payment.getAmount(), payment.getStatus(),
                payment.getFailureReason(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
