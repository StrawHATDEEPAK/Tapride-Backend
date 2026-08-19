# TapRide — Event-Driven Ride Booking Platform

A backend-first portfolio project demonstrating distributed transaction handling
(saga pattern), event-driven architecture, and production-grade observability,
built around a ride-booking domain.

**Stack:** Java 21 (virtual threads) · Spring Boot 3.3 · Maven · Kafka · PostgreSQL
· Redis · Resilience4j · Prometheus/Grafana · OpenTelemetry/Jaeger · Docker/Kubernetes

---

## Status: Day 1 of 7 ✅

- [x] Repo scaffold + docker-compose infra (Postgres ×3, Kafka via KRaft, Redis, Kafka UI)
- [x] Root parent POM (`pom.xml`) — shares Spring Boot/Testcontainers/Resilience4j versions
      across services via `<dependencyManagement>`, deliberately NOT a multi-module reactor
      (no `<modules>` list), so each service stays independently buildable/deployable
- [x] `order-service`: ride domain model, state machine, event log (event-sourcing-lite),
      REST API, Kafka producer (transactional-outbox-simplified pattern), saga listener stubs,
      Haversine-based mock fare estimation
- [x] `payment-service`: mock payment authorization/refund, saga participant pattern,
      chaos-injectable failures (`/api/chaos`) for demoing the saga's compensation path
- [x] `matching-service`: Redis geospatial driver search (GEOADD/GEOSEARCH), chaos-injectable
      match failures, and a scheduled `DriverLocationSimulator` that ticks matched drivers
      toward pickup and publishes `DRIVER_LOCATION_UPDATED` events — groundwork for the
      end-of-project live-map visualization, deferred but schema-ready now
- [x] Day 4: `order-service` chaos hook + Resilience4j `@Retry`/`@CircuitBreaker` stacked on
      the ride-lookup endpoint, fixed to ignore legitimate 404s (`NoSuchElementException`)
      so normal traffic can't trip the breaker; real Testcontainers integration test
      (Postgres + Kafka) proving the full request→validate→persist→publish chain
- [ ] Day 5: Observability stack (Prometheus/Grafana/Jaeger, correlated logs)
- [ ] Day 6: Frontend — live order feed via WebSocket + chaos button
- [ ] Day 7: CI/CD, README polish, demo recording

---

## Architecture: the saga

`order-service` is the **saga orchestrator**. It owns the ride's state machine and
drives the flow forward by publishing "please do X" events and reacting to
"X succeeded/failed" events from participant services:

```
RIDE REQUESTED
   -> VALIDATED (sync check)
   -> PAYMENT_PENDING          --publish--> payment-service authorizes
   <- PAYMENT_AUTHORIZED       --consume--
   -> DRIVER_MATCHING          --publish--> matching-service finds a driver
   <- DRIVER_MATCHED           --consume--
   -> IN_PROGRESS -> COMPLETED

Compensation (rollback) arm:
   payment fails      -> PAYMENT_FAILED -> CANCELLED
   matching fails      -> MATCH_FAILED -> PAYMENT_REFUNDING -> CANCELLED
```

Every transition is guarded by `RideStateMachine` (an explicit transition table —
see `order-service/src/main/java/com/tapride/order/domain/RideStateMachine.java`),
so illegal transitions fail loudly instead of corrupting state.

Every transition also appends to an **append-only event log** (`ride_events` table)
before publishing to Kafka — the log is the actual source of truth; the `rides`
table is a materialized read view. Kafka publish is deferred until after the DB
transaction commits (via `TransactionSynchronizationManager`), which avoids
publishing an event for a write that got rolled back — a simplified version of
the transactional outbox pattern (documented as a known scope trade-off; a full
outbox table + relay process is the textbook-correct version for exactly-once
delivery).

---

## The saga event contract (order-service ↔ payment-service)

Both services publish plain, class-agnostic JSON to Kafka — deliberately not a
shared Java DTO library — so each service can evolve its internals without
breaking the other. The contract is just field names on the wire:

**order-service publishes to `tapride.ride.events`** (its own event log, which
doubles as its outbound commands):
```json
{"eventId": "...", "rideId": "...", "eventType": "PAYMENT_AUTHORIZATION_REQUESTED",
 "payloadJson": "{\"rideId\":\"...\",\"estimatedFare\":12.34}", "correlationId": "...", "occurredAt": "..."}
```

**payment-service consumes that topic**, filters for `PAYMENT_AUTHORIZATION_REQUESTED`
and `PAYMENT_REFUND_REQUESTED`, and ignores everything else (RIDE_REQUESTED,
DRIVER_MATCHED, etc. — those aren't addressed to it).

**payment-service publishes to `tapride.payment.events`**:
```json
{"type": "PAYMENT_AUTHORIZED", "rideId": "...", "correlationId": "...", "amount": 12.34}
{"type": "PAYMENT_FAILED", "rideId": "...", "correlationId": "...", "reason": "mock_gateway_declined", "amount": 12.34}
```

**order-service's `SagaEventListener` consumes that topic** and drives the ride
state machine forward or into compensation accordingly.

`matching-service` (Day 3) follows the identical pattern on `tapride.matching.events`:
publishes `DRIVER_MATCHED` (with `driverId`) or `DRIVER_MATCH_FAILED` in response to
`DRIVER_MATCH_REQUESTED`, which now also carries `pickupLat`/`pickupLng` so the
Redis geospatial search has something to search around.

---

## Driver matching (Day 3)

`matching-service` seeds ~10 fake drivers on startup, scattered randomly around
a configurable center point (defaults to Indore, matching this README's test
coordinates), into both Postgres (`drivers` — static profile) and Redis
(`drivers:available` / `drivers:location` — live GEO index). When a
`DRIVER_MATCH_REQUESTED` event arrives, it runs a real `GEOSEARCH` for the
nearest available driver within 5km, reserves them, and reports back.

A `DriverLocationSimulator` scheduled task then ticks every matched driver's
position toward the pickup point every few seconds, publishing
`DRIVER_LOCATION_UPDATED` events — nothing consumes these yet (that's the
Day 6 / end-of-project live-map work), but the data is flowing and ready.

```bash
# Check a match once a ride has been matched (status will be DRIVER_MATCHING -> DRIVER_MATCHED)
curl http://localhost:8083/api/matches/by-ride/{rideId}

# Run it again a few seconds later - currentLat/currentLng should have moved
# closer to pickupLat/pickupLng
curl http://localhost:8083/api/matches/by-ride/{rideId}

# Force every match to fail (demos the DEEPER compensation path: payment
# already succeeded, so order-service must refund before cancelling)
curl -X PUT http://localhost:8083/api/chaos -H "Content-Type: application/json" -d '{"forceFailure": true}'
curl http://localhost:8083/api/chaos/reset
```

---

## Demoing the saga's failure path (chaos injection)

`payment-service` exposes a live chaos control surface — this is the "senior
engineer" moment of the demo, showing the saga's compensation logic actually work:

```bash
# Force every payment to fail (guaranteed, repeatable demo)
curl -X PUT http://localhost:8082/api/chaos -H "Content-Type: application/json" -d '{"forceFailure": true}'

# Create a ride while chaos is on -> watch it go PAYMENT_PENDING -> CANCELLED
curl -X POST http://localhost:8081/api/rides -H "Content-Type: application/json" \
  -d '{"riderId":"11111111-1111-1111-1111-111111111111","pickupLat":22.72,"pickupLng":75.86,"dropoffLat":22.75,"dropoffLng":75.90}'

# Check the ride's full event log - you'll see RIDE_REQUESTED, RIDE_VALIDATED,
# PAYMENT_AUTHORIZATION_REQUESTED, then (from order-service's saga listener
# reacting to payment-service's PAYMENT_FAILED) PAYMENT_FAILED, RIDE_CANCELLED
curl http://localhost:8081/api/rides/{id}/events

# Reset back to normal (15% random failure rate, no forced failures)
curl -X POST http://localhost:8082/api/chaos/reset
```

---

## Resilience (Day 4): Retry + Circuit Breaker

`GET /api/rides/{id}` has two Resilience4j layers stacked on it, both configured
in `order-service/src/main/resources/application.yml` under `resilience4j.*`:

1. **`@Retry`** — up to 3 attempts, 200ms apart, before giving up on a single request
2. **`@CircuitBreaker`** — watches the failure rate across a sliding window of the
   *last 10 requests*; if 50%+ fail, it trips OPEN for 10s and every request during
   that window short-circuits straight to a 503 fallback instead of even trying —
   protecting a struggling dependency from being hammered further

**A real bug this caught and fixed**: by default, Resilience4j counts *every*
exception as a failure — including a plain "ride not found." That would mean
enough people innocently checking nonexistent ride IDs could trip the breaker
for everyone. Fixed via `ignore-exceptions: [java.util.NoSuchElementException]`
on both the retry and circuit-breaker config — a genuine 404 now bypasses both
layers entirely and returns instantly, exactly as it should.

Since `order-service` has no real external dependency call on this read path
(it only reads its own database), a chaos hook (`ChaosSettings` /
`ChaosController`) simulates one, purely so this config has something real to
react to:

```bash
# Force every lookup to fail (simulated infra fault, NOT a 404)
curl -X PUT http://localhost:8081/api/chaos -H "Content-Type: application/json" -d '{"forceFailure": true}'

# Hit it a few times in a row - first few calls retry 3x then return 503;
# after ~5 failures in the 10-request window, the breaker trips OPEN and
# subsequent calls fail INSTANTLY (no retry delay) until it resets ~10s later
curl -w "\n%{time_total}s\n" http://localhost:8081/api/rides/{any-id}

# A genuine 404 during this whole time still returns instantly and correctly -
# proving the ignore-exceptions fix works
curl http://localhost:8081/api/rides/00000000-0000-0000-0000-000000000000

curl -X POST http://localhost:8081/api/chaos/reset
```

---

## Integration testing

`OrderServiceIntegrationTest` uses real Testcontainers-managed Postgres and
Kafka (not mocks) to prove the full chain works together: Flyway migrations
apply cleanly, the HTTP layer accepts a request, `RideService` validates and
persists it, and the saga's first events get published — all through actual
infrastructure, not stubbed. Run it with:

```bash
cd order-service
mvn test -Dtest=OrderServiceIntegrationTest
```

(Requires Docker running locally, since Testcontainers needs it to spin up
the ephemeral Postgres/Kafka containers for the test's lifetime.)

---

## Build locally

Each service is built independently, referencing the shared parent POM at the
repo root:

```bash
cd order-service
mvn clean verify
```

## Running via Docker

Note: each service's Docker build context is the **repo root** (not the
service's own folder), since every service inherits the parent `pom.xml`.
This is already configured in `docker-compose.yml` — just run:

```bash
docker compose up -d order-db payment-db matching-db redis kafka kafka-ui
docker compose up --build order-service payment-service matching-service
```

- order-service: http://localhost:8081
- payment-service: http://localhost:8082
- matching-service: http://localhost:8083
- Kafka UI: http://localhost:8090 (watch `tapride.ride.events`,
  `tapride.payment.events`, and `tapride.matching.events` fill up as rides
  move through the saga)

- Kafka UI: http://localhost:8090
- order-service: http://localhost:8081/api/rides
- Actuator health: http://localhost:8081/actuator/health
- Prometheus metrics: http://localhost:8081/actuator/prometheus

## Quick API test (once running)

```bash
curl -X POST http://localhost:8081/api/rides \
  -H "Content-Type: application/json" \
  -d '{"riderId":"11111111-1111-1111-1111-111111111111","pickupLat":22.72,"pickupLng":75.86,"dropoffLat":22.75,"dropoffLng":75.90}'
```

---

## Note on this environment

The Maven build was NOT run/verified in the generation sandbox — outbound network
here is restricted to a small allowlist that does not include Maven Central, so
`mvn package` needs to be run on your own machine to fetch dependencies and confirm
a clean compile. The code follows standard Spring Boot 3 / Java 21 conventions
throughout, but run `mvn clean verify` locally as your first step before Day 2.
