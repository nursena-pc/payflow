DROP INDEX idx_kafka_dead_letter_command_audits_operator_time;
DROP INDEX idx_kafka_dead_letter_command_audits_record_time;
DROP INDEX idx_kafka_dead_letter_command_audits_type_time;
DROP INDEX idx_kafka_dead_letter_command_audits_occurred_at;

CREATE INDEX idx_kafka_dead_letter_command_audits_occurred_id
    ON kafka_dead_letter_command_audits (
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_kafka_dead_letter_command_audits_operator_time_id
    ON kafka_dead_letter_command_audits (
        operator_id,
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_kafka_dead_letter_command_audits_record_time_id
    ON kafka_dead_letter_command_audits (
        dead_letter_record_id,
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_kafka_dead_letter_command_audits_type_time_id
    ON kafka_dead_letter_command_audits (
        command_type,
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_kafka_dead_letter_command_audits_stage_time_id
    ON kafka_dead_letter_command_audits (
        stage,
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_kafka_dead_letter_command_audits_outcome_time_id
    ON kafka_dead_letter_command_audits (
        outcome,
        occurred_at DESC,
        id DESC
    );
