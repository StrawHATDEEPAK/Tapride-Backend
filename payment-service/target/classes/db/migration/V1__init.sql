CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    ride_id         UUID NOT NULL UNIQUE,
    amount          NUMERIC(10, 2) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_payments_status ON payments (status);

CREATE TABLE payment_events (
    id              UUID PRIMARY KEY,
    ride_id         UUID NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    payload         TEXT,
    correlation_id  VARCHAR(64) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payment_events_ride_id ON payment_events (ride_id, occurred_at);
CREATE INDEX idx_payment_events_correlation_id ON payment_events (correlation_id);
