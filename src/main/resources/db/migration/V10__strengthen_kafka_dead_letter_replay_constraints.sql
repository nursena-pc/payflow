ALTER TABLE kafka_dead_letter_records
    ADD COLUMN replay_origin_id UUID;

ALTER TABLE kafka_dead_letter_records
    ADD COLUMN replay_attempt_base INTEGER
        NOT NULL
        DEFAULT 0;

UPDATE kafka_dead_letter_records
SET replay_origin_id = id
WHERE replay_origin_id IS NULL;

ALTER TABLE kafka_dead_letter_records
    ALTER COLUMN replay_origin_id
    SET NOT NULL;

ALTER TABLE kafka_dead_letter_records
    ALTER COLUMN replay_attempt_base
    DROP DEFAULT;

ALTER TABLE kafka_dead_letter_records
    ADD CONSTRAINT
        chk_kafka_dead_letter_records_replay_attempt_base
    CHECK (
        replay_attempt_base >= 0
    );

ALTER TABLE kafka_dead_letter_records
    ADD CONSTRAINT
        chk_kafka_dead_letter_records_replay_lineage
    CHECK (
        (
            replay_attempt_base = 0
            AND replay_origin_id = id
        )
        OR
        (
            replay_attempt_base > 0
            AND replay_origin_id <> id
        )
    );

ALTER TABLE kafka_dead_letter_records
    ADD CONSTRAINT
        chk_kafka_dead_letter_records_total_replay_count
    CHECK (
        replay_attempt_base
            <= 2147483647 - replay_count
    );

ALTER TABLE kafka_dead_letter_records
    ADD CONSTRAINT
        chk_kafka_dead_letter_records_replay_timestamp
    CHECK (
        last_replayed_at IS NULL
        OR last_replayed_at >= received_at
    );

ALTER TABLE kafka_dead_letter_records
    ADD CONSTRAINT
        chk_kafka_dead_letter_records_replay_lease_period
    CHECK (
        status <> 'REPLAYING'
        OR replay_lease_until > last_replayed_at
    );

CREATE INDEX
    idx_kafka_dead_letter_records_replay_origin
ON kafka_dead_letter_records (
    replay_origin_id,
    received_at
);
