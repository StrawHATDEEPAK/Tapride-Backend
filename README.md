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
      REST API, Kafka producer (transactional-outbox-simplified pattern), saga listener stubs
- [ ] Day 2: `payment-service` + saga orchestration wired end-to-end
- [ ] Day 3: `matching-service` + Redis geospatial matching
- [ ] Day 4: Resilience4j circuit breakers + `/chaos` endpoint + Testcontainers integration tests
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

## Build locally

Each service is built independently, referencing the shared parent POM at the
repo root:

```bash
cd order-service
mvn clean verify
```

## Running via Docker (once payment/matching services exist — Day 2+)

Note: order-service's Docker build context is the **repo root** (not
`order-service/`), since it needs access to the parent `pom.xml`. This is
already configured in `docker-compose.yml` — just run:

```bash
docker compose up -d order-db payment-db matching-db redis kafka kafka-ui
docker compose up --build order-service
```

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
