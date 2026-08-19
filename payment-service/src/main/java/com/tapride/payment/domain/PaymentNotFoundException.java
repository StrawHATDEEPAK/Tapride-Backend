package com.tapride.payment.domain;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID rideId) {
        super("No payment found for ride: " + rideId);
    }
}