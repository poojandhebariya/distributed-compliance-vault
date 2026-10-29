
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id),
    event_type VARCHAR(150) NOT NULL,
    claim_token UUID NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING'
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),

    attempts INTEGER NOT NULL DEFAULT 1
        CHECK (attempts >= 0),

    last_error TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,

    CONSTRAINT processed_events_completed_timestamp
        CHECK (status <> 'COMPLETED' OR processed_at IS NOT NULL)
);

CREATE INDEX idx_processed_events_retry
    ON processed_events (next_attempt_at, started_at)
    WHERE status IN ('PROCESSING', 'FAILED');
