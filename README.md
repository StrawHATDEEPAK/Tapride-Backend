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
- [ ] Day 3: `matching-service` + Redis geospatial matching + `DRIVER_LOCATION_UPDATED` ticks
- [ ] Day 4: Resilience4j circuit breakers + `/chaos` endpoint polish + Testcontainers integration tests
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

`matching-service` (Day 3) will follow the identical pattern on `tapride.matching.events`.

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
docker compose up --build order-service payment-service
```

- order-service: http://localhost:8081
- payment-service: http://localhost:8082
- Kafka UI: http://localhost:8090 (watch `tapride.ride.events` and
  `tapride.payment.events` fill up as rides move through the saga)

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
