CREATE TABLE processed_kafka_events (
    consumer_name VARCHAR(200) NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    event_version INTEGER NOT NULL,
    topic VARCHAR(200) NOT NULL,
    partition_number INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_processed_kafka_events
        PRIMARY KEY (
            consumer_name,
            event_id
        ),

    CONSTRAINT chk_processed_kafka_events_consumer_name
        CHECK (
            btrim(consumer_name) <> ''
        ),

    CONSTRAINT chk_processed_kafka_events_event_type
        CHECK (
            btrim(event_type) <> ''
        ),

    CONSTRAINT chk_processed_kafka_events_event_version
        CHECK (
            event_version > 0
        ),

    CONSTRAINT chk_processed_kafka_events_topic
        CHECK (
            btrim(topic) <> ''
        ),

    CONSTRAINT chk_processed_kafka_events_partition
        CHECK (
            partition_number >= 0
        ),

    CONSTRAINT chk_processed_kafka_events_offset
        CHECK (
            record_offset >= 0
        )
);

CREATE INDEX idx_processed_kafka_events_processed_at
    ON processed_kafka_events (
        processed_at
    );
