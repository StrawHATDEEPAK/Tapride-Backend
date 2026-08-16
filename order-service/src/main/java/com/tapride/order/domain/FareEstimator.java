package com.tapride.order.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Mock fare estimation using the Haversine formula for distance, plus a flat
 * base fare and a per-km rate. Deliberately simple - a real system would price
 * on surge/demand/vehicle-type, but this is enough to give payment-service a
 * realistic, non-hardcoded amount to authorize against.
 */
@Component
public class FareEstimator {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final BigDecimal BASE_FARE = new BigDecimal("2.50");
    private static final BigDecimal PER_KM_RATE = new BigDecimal("1.20");

    public BigDecimal estimate(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        double distanceKm = haversineKm(pickupLat, pickupLng, dropoffLat, dropoffLng);
        BigDecimal distanceCharge = PER_KM_RATE.multiply(BigDecimal.valueOf(distanceKm));
        return BASE_FARE.add(distanceCharge).setScale(2, RoundingMode.HALF_UP);
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
