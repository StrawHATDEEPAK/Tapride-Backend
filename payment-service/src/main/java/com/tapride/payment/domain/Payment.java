package com.tapride.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** One payment per ride; also our natural lookup key from saga events. */
    @Column(nullable = false, unique = true)
    private UUID rideId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    /** Populated only when status is FAILED - shown in the event log and API for debugging. */
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public Payment(UUID rideId, BigDecimal amount) {
        this.rideId = rideId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyStatus(PaymentStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }
}
