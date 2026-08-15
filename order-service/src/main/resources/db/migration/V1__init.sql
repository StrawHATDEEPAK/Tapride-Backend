CREATE TABLE rides (
    id              UUID PRIMARY KEY,
    rider_id        UUID NOT NULL,
    driver_id       UUID,
    pickup_lat      DOUBLE PRECISION NOT NULL,
    pickup_lng      DOUBLE PRECISION NOT NULL,
    dropoff_lat     DOUBLE PRECISION NOT NULL,
    dropoff_lng     DOUBLE PRECISION NOT NULL,
    status          VARCHAR(32) NOT NULL,
    fare            NUMERIC(10, 2),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_rides_rider_id ON rides (rider_id);
CREATE INDEX idx_rides_status ON rides (status);

CREATE TABLE ride_events (
    id              UUID PRIMARY KEY,
    ride_id         UUID NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    payload         TEXT,
    correlation_id  VARCHAR(64) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ride_events_ride_id ON ride_events (ride_id, occurred_at);
CREATE INDEX idx_ride_events_correlation_id ON ride_events (correlation_id);
