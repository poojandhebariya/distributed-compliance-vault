
ALTER TABLE outbox_events
    ADD COLUMN last_error TEXT,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX idx_outbox_retry_eligible
    ON outbox_events (next_attempt_at, created_at)
    WHERE published_at IS NULL;
