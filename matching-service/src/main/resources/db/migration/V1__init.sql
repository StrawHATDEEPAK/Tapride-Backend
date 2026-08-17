CREATE TABLE drivers (
    id      UUID PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    vehicle VARCHAR(50) NOT NULL
);

CREATE TABLE driver_matches (
    id          UUID PRIMARY KEY,
    ride_id     UUID NOT NULL UNIQUE,
    driver_id   UUID NOT NULL,
    status      VARCHAR(16) NOT NULL,
    pickup_lat  DOUBLE PRECISION NOT NULL,
    pickup_lng  DOUBLE PRECISION NOT NULL,
    current_lat DOUBLE PRECISION NOT NULL,
    current_lng DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_driver_matches_status ON driver_matches (status);
CREATE INDEX idx_driver_matches_driver_id ON driver_matches (driver_id);

CREATE TABLE match_events (
    id             UUID PRIMARY KEY,
    ride_id        UUID NOT NULL,
    event_type     VARCHAR(32) NOT NULL,
    payload        TEXT,
    correlation_id VARCHAR(64) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_match_events_ride_id ON match_events (ride_id, occurred_at);
CREATE INDEX idx_match_events_correlation_id ON match_events (correlation_id);
