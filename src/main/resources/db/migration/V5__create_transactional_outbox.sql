CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    event_version INTEGER NOT NULL,
    topic VARCHAR(200) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    deduplication_key VARCHAR(300) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,
    locked_by VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),

    CONSTRAINT uq_outbox_events_deduplication_key
        UNIQUE (deduplication_key),

    CONSTRAINT chk_outbox_events_event_version
        CHECK (event_version > 0),

    CONSTRAINT chk_outbox_events_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT chk_outbox_events_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'PUBLISHED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_outbox_events_payload
        CHECK (
            jsonb_typeof(payload) = 'object'
            AND payload <> '{}'::jsonb
        ),

    CONSTRAINT chk_outbox_events_availability
        CHECK (available_at >= created_at),

    CONSTRAINT chk_outbox_events_processing_lock
        CHECK (
            (
                status = 'PROCESSING'
                AND locked_at IS NOT NULL
                AND locked_until IS NOT NULL
                AND locked_by IS NOT NULL
                AND locked_until > locked_at
            )
            OR
            (
                status <> 'PROCESSING'
                AND locked_at IS NULL
                AND locked_until IS NULL
                AND locked_by IS NULL
            )
        ),

    CONSTRAINT chk_outbox_events_publication
        CHECK (
            (
                status = 'PUBLISHED'
                AND published_at IS NOT NULL
                AND published_at >= created_at
            )
            OR
            (
                status <> 'PUBLISHED'
                AND published_at IS NULL
            )
        )
);

CREATE INDEX idx_outbox_events_pending_available
    ON outbox_events (
        available_at,
        created_at,
        id
    )
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_events_processing_lease
    ON outbox_events (
        locked_until,
        created_at,
        id
    )
    WHERE status = 'PROCESSING';

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (
        aggregate_type,
        aggregate_id,
        created_at DESC
    );
