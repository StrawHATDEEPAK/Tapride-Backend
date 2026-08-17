package com.tapride.matching.simulation;

import com.tapride.matching.domain.Driver;
import com.tapride.matching.domain.DriverLocationIndex;
import com.tapride.matching.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seeds a small fake driver fleet on startup, scattered randomly within a
 * configurable radius of a center point (defaults to Indore, matching the
 * test coordinates used throughout this project's demo/README). Runs on
 * EVERY startup - Driver rows use a fixed, deterministic UUID list so re-runs
 * are idempotent (upsert via saveAll, not duplicate inserts), and the Redis
 * GEO index is always repopulated from scratch since Redis data doesn't
 * survive a fresh `docker compose down -v` the way Postgres migrations do.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverSeeder implements CommandLineRunner {

    private static final List<String> DRIVER_NAMES = List.of(
            "Arjun", "Priya", "Rahul", "Sneha", "Vikram", "Anjali", "Karan", "Neha", "Rohan", "Divya");
    private static final List<String> VEHICLES = List.of(
            "Hatchback", "Sedan", "SUV", "Auto-rickshaw");

    private final DriverRepository driverRepository;
    private final DriverLocationIndex driverLocationIndex;

    @Value("${matching.seed.center-lat:22.7196}")
    private double centerLat;
    @Value("${matching.seed.center-lng:75.8577}")
    private double centerLng;
    @Value("${matching.seed.driver-count:10}")
    private int driverCount;
    @Value("${matching.seed.spread-km:8.0}")
    private double spreadKm;

    @Override
    public void run(String... args) {
        List<Driver> drivers = driverRepository.findAll();
        if (drivers.isEmpty()) {
            drivers = createDrivers();
            driverRepository.saveAll(drivers);
            log.info("Seeded {} drivers into Postgres", drivers.size());
        }

        // Always repopulate Redis - it's a live/ephemeral index, not durable
        // state, so it needs re-seeding after every fresh container start.
        for (Driver driver : drivers) {
            double[] point = randomPointNear(centerLat, centerLng, spreadKm);
            driverLocationIndex.markAvailable(driver.getId(), point[0], point[1]);
        }
        log.info("Seeded {} driver locations into Redis GEO index (center: {}, {})",
                drivers.size(), centerLat, centerLng);
    }

    private List<Driver> createDrivers() {
        return java.util.stream.IntStream.range(0, driverCount)
                .mapToObj(i -> new Driver(
                        UUID.nameUUIDFromBytes(("tapride-driver-" + i).getBytes()), // deterministic ID, idempotent reseeding
                        DRIVER_NAMES.get(i % DRIVER_NAMES.size()),
                        VEHICLES.get(i % VEHICLES.size())))
                .toList();
    }

    /** Random point within spreadKm of center - crude but fine for demo purposes (no great-circle correction). */
    private double[] randomPointNear(double lat, double lng, double spreadKm) {
        double kmPerDegreeLat = 111.0;
        double kmPerDegreeLng = 111.0 * Math.cos(Math.toRadians(lat));

        double dLat = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * spreadKm / kmPerDegreeLat;
        double dLng = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * spreadKm / kmPerDegreeLng;

        return new double[]{lat + dLat, lng + dLng};
    }
}
