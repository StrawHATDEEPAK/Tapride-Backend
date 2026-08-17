package com.tapride.matching.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Same chaos-injection pattern as payment-service's ChaosSettings. Here it
 * demos a DIFFERENT compensation path than payment failure does: a driver
 * match failure happens AFTER payment already succeeded, so order-service has
 * to unwind further - refund the payment, THEN cancel - rather than just
 * cancelling outright. Two distinct rollback depths, same underlying pattern.
 */
@Component
public class ChaosSettings {

    private final AtomicReference<Double> failureRate;
    private final AtomicBoolean forceFailure = new AtomicBoolean(false);

    public ChaosSettings(@Value("${matching.mock.failure-rate:0.10}") double defaultFailureRate) {
        this.failureRate = new AtomicReference<>(defaultFailureRate);
    }

    public boolean shouldFail() {
        if (forceFailure.get()) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < failureRate.get();
    }

    public double getFailureRate() {
        return failureRate.get();
    }

    public void setFailureRate(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("failureRate must be between 0.0 and 1.0");
        }
        failureRate.set(rate);
    }

    public boolean isForceFailure() {
        return forceFailure.get();
    }

    public void setForceFailure(boolean value) {
        forceFailure.set(value);
    }
}
