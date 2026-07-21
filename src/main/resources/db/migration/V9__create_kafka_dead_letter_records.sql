CREATE TABLE kafka_dead_letter_records (
    id UUID PRIMARY KEY,

    dlt_topic VARCHAR(200) NOT NULL,
    dlt_partition INTEGER NOT NULL,
    dlt_offset BIGINT NOT NULL,

    original_topic VARCHAR(200) NOT NULL,
    original_partition INTEGER NOT NULL,
    original_offset BIGINT NOT NULL,
    original_consumer_group VARCHAR(255) NOT NULL,

    record_key TEXT,
    payload TEXT,

    exception_type VARCHAR(500) NOT NULL,
    exception_message TEXT,

    status VARCHAR(30) NOT NULL,
    replay_count INTEGER NOT NULL,

    received_at TIMESTAMPTZ NOT NULL,
    last_replayed_at TIMESTAMPTZ,

    replay_lease_owner VARCHAR(200),
    replay_lease_until TIMESTAMPTZ,

    last_replay_error TEXT,

    CONSTRAINT uq_kafka_dead_letter_records_location
        UNIQUE (
            dlt_topic,
            dlt_partition,
            dlt_offset
        ),

    CONSTRAINT chk_kafka_dead_letter_records_dlt_topic
        CHECK (
            btrim(dlt_topic) <> ''
        ),

    CONSTRAINT chk_kafka_dead_letter_records_dlt_partition
        CHECK (
            dlt_partition >= 0
        ),

    CONSTRAINT chk_kafka_dead_letter_records_dlt_offset
        CHECK (
            dlt_offset >= 0
        ),

    CONSTRAINT chk_kafka_dead_letter_records_original_topic
        CHECK (
            btrim(original_topic) <> ''
        ),

    CONSTRAINT chk_kafka_dead_letter_records_original_partition
        CHECK (
            original_partition >= 0
        ),

    CONSTRAINT chk_kafka_dead_letter_records_original_offset
        CHECK (
            original_offset >= 0
        ),

    CONSTRAINT chk_kafka_dead_letter_records_consumer_group
        CHECK (
            btrim(original_consumer_group) <> ''
        ),

    CONSTRAINT chk_kafka_dead_letter_records_exception_type
        CHECK (
            btrim(exception_type) <> ''
        ),

    CONSTRAINT chk_kafka_dead_letter_records_status
        CHECK (
            status IN (
                'RECEIVED',
                'REPLAYING',
                'REPLAYED',
                'REPLAY_FAILED',
                'DISCARDED'
            )
        ),

    CONSTRAINT chk_kafka_dead_letter_records_replay_count
        CHECK (
            replay_count >= 0
        ),

    CONSTRAINT chk_kafka_dead_letter_records_replay_time
        CHECK (
            (
                replay_count = 0
                AND last_replayed_at IS NULL
            )
            OR
            (
                replay_count > 0
                AND last_replayed_at IS NOT NULL
            )
        ),

    CONSTRAINT chk_kafka_dead_letter_records_received_state
        CHECK (
            status <> 'RECEIVED'
            OR replay_count = 0
        ),

    CONSTRAINT chk_kafka_dead_letter_records_attempt_state
        CHECK (
            status NOT IN (
                'REPLAYING',
                'REPLAYED',
                'REPLAY_FAILED'
            )
            OR replay_count > 0
        ),

    CONSTRAINT chk_kafka_dead_letter_records_replay_lease
        CHECK (
            (
                status = 'REPLAYING'
                AND replay_lease_owner IS NOT NULL
                AND btrim(replay_lease_owner) <> ''
                AND replay_lease_until IS NOT NULL
            )
            OR
            (
                status <> 'REPLAYING'
                AND replay_lease_owner IS NULL
                AND replay_lease_until IS NULL
            )
        )
);

CREATE INDEX idx_kafka_dead_letter_records_status_received
    ON kafka_dead_letter_records (
        status,
        received_at DESC
    );

CREATE INDEX idx_kafka_dead_letter_records_replay_lease
    ON kafka_dead_letter_records (
        replay_lease_until
    )
    WHERE status = 'REPLAYING';
