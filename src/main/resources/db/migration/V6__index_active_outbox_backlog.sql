CREATE INDEX idx_outbox_events_active_created_at
    ON outbox_events (created_at)
    WHERE status IN (
        'PENDING',
        'PROCESSING'
    );
