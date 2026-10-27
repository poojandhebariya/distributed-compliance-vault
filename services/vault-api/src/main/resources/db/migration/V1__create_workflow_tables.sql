
-- V1: Create the initial workflow database schema.

CREATE TABLE documents (
                           id UUID PRIMARY KEY,
                           original_filename VARCHAR(255) NOT NULL,
                           content_type VARCHAR(100) NOT NULL,
                           object_key VARCHAR(512) NOT NULL UNIQUE,
                           status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
                           created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                           updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                           CONSTRAINT chk_documents_status
                               CHECK (status IN (
                                                 'RECEIVED',
                                                 'PROCESSING',
                                                 'COMPLETED',
                                                 'FAILED'
                                   ))
);

CREATE TABLE idempotency_records (
                                     idempotency_key VARCHAR(255) PRIMARY KEY,
                                     request_fingerprint VARCHAR(64) NOT NULL,
                                     document_id UUID REFERENCES documents(id),
                                     response_status INTEGER,
                                     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY,
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id UUID NOT NULL,
                               event_type VARCHAR(150) NOT NULL,
                               payload JSONB NOT NULL,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               published_at TIMESTAMPTZ,
                               attempts INTEGER NOT NULL DEFAULT 0,

                               CONSTRAINT chk_outbox_attempts
                                   CHECK (attempts >= 0)
);

CREATE INDEX idx_documents_status_created
    ON documents (status, created_at);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
