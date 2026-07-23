DROP INDEX
    idx_kafka_dead_letter_records_status_received;

CREATE INDEX
    idx_kafka_dead_letter_records_status_received_id
ON kafka_dead_letter_records (
    status,
    received_at DESC,
    id DESC
);

CREATE INDEX
    idx_kafka_dead_letter_records_received_id
ON kafka_dead_letter_records (
    received_at DESC,
    id DESC
);
