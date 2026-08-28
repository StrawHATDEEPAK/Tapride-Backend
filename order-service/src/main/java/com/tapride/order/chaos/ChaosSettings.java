package com.tapride.order.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Same chaos-injection pattern as payment-service and matching-service's
 * ChaosSettings, but with a different purpose: order-service has no real
 * external dependency call to inject failure into (payment/matching services
 * talk to a "mock gateway" and a "mock driver pool" respectively - order-service
 * just reads its own database). So instead this simulates a flaky DOWNSTREAM
 * DEPENDENCY on the ride-lookup path specifically, giving the already-configured
 * Resilience4j circuit breaker + retry (see application.yml, instance
 * "rideLookup") something genuine to react to. Without this, that config is
 * inert - nothing in the code path ever actually fails, so the breaker would
 * never open no matter how it's tuned.
 */
@Component
public class ChaosSettings {

    private final AtomicReference<Double> failureRate;
    private final AtomicBoolean forceFailure = new AtomicBoolean(false);

    public ChaosSettings(@Value("${order.mock.lookup-failure-rate:0.0}") double defaultFailureRate) {
        // Defaults to 0.0 (off) unlike payment/matching-service's chaos, since
        // order-service's own read path failing isn't something you'd want
        // randomly happening in the background during normal Day 1-3 testing -
        // this is opt-in, switched on specifically to demo the circuit breaker.
        this.failureRate = new AtomicReference<>(defaultFailureRate);
    }

    /** @throws DownstreamUnavailableException to simulate an infra fault, if chaos says so. */
    public void maybeFail() {
        if (forceFailure.get() || ThreadLocalRandom.current().nextDouble() < failureRate.get()) {
            throw new DownstreamUnavailableException("Simulated downstream failure (chaos-injected)");
        }
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

    /**
     * Deliberately a distinct exception type from NoSuchElementException - this
     * is what lets the circuit breaker/retry config tell "genuine infra trouble"
     * apart from "ride legitimately doesn't exist" (see the ignore-exceptions
     * config in application.yml).
     */
    public static class DownstreamUnavailableException extends RuntimeException {
        public DownstreamUnavailableException(String message) {
            super(message);
        }
    }
}
