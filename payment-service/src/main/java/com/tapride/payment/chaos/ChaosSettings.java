package com.tapride.payment.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central control point for injecting payment failures on demand - this is what
 * the Day 4 "/chaos" endpoint and live demo will toggle to show the saga's
 * compensation path (payment fails -> order-service rolls the ride back) and
 * Resilience4j's circuit breaker reacting to a spike in failures, without
 * having to physically break something to prove the system handles failure.
 *
 * Two independent levers:
 *   - failureRate: a probability (0.0-1.0) that any given authorization randomly fails
 *   - forceFailure: an explicit override that fails every authorization, for a
 *     guaranteed, repeatable demo moment rather than relying on randomness
 */
@Component
public class ChaosSettings {

    private final AtomicReference<Double> failureRate;
    private final AtomicBoolean forceFailure = new AtomicBoolean(false);

    public ChaosSettings(@Value("${payment.mock.failure-rate:0.15}") double defaultFailureRate) {
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
