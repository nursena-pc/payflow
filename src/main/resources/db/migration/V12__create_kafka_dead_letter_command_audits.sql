CREATE TABLE kafka_dead_letter_command_audits (
    id UUID PRIMARY KEY,
    command_id UUID NOT NULL,
    stage VARCHAR(20) NOT NULL,
    operator_id UUID NOT NULL,
    dead_letter_record_id UUID NOT NULL,
    command_type VARCHAR(20) NOT NULL,
    outcome VARCHAR(40),
    error_code VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_kafka_dead_letter_command_audits_command_stage
        UNIQUE (
            command_id,
            stage
        ),

    CONSTRAINT chk_kafka_dead_letter_command_audits_stage
        CHECK (
            stage IN (
                'ATTEMPTED',
                'COMPLETED'
            )
        ),

    CONSTRAINT chk_kafka_dead_letter_command_audits_type
        CHECK (
            command_type IN (
                'REPLAY',
                'DISCARD'
            )
        ),

    CONSTRAINT chk_kafka_dead_letter_command_audits_outcome
        CHECK (
            outcome IS NULL
            OR outcome IN (
                'REPLAYED',
                'REPLAY_NOT_FOUND',
                'REPLAY_NOT_CLAIMABLE',
                'REPLAY_FAILED',
                'REPLAY_UNRESOLVED',
                'DISCARDED',
                'ALREADY_DISCARDED',
                'DISCARD_NOT_FOUND',
                'DISCARD_NOT_DISCARDABLE',
                'INTERNAL_FAILURE'
            )
        ),

    CONSTRAINT chk_kafka_dead_letter_command_audits_error_code
        CHECK (
            error_code IS NULL
            OR error_code IN (
                'KAFKA_DEAD_LETTER_RECORD_NOT_FOUND',
                'KAFKA_DEAD_LETTER_RECORD_NOT_CLAIMABLE',
                'KAFKA_DEAD_LETTER_REPLAY_FAILED',
                'KAFKA_DEAD_LETTER_REPLAY_UNRESOLVED',
                'KAFKA_DEAD_LETTER_RECORD_NOT_DISCARDABLE',
                'KAFKA_DEAD_LETTER_COMMAND_INTERNAL_FAILURE'
            )
        ),

    CONSTRAINT chk_kafka_dead_letter_command_audits_stage_state
        CHECK (
            (
                stage = 'ATTEMPTED'
                AND outcome IS NULL
                AND error_code IS NULL
            )
            OR
            (
                stage = 'COMPLETED'
                AND outcome IS NOT NULL
            )
        ),

    CONSTRAINT chk_kafka_dead_letter_command_audits_command_outcome
        CHECK (
            outcome IS NULL
            OR
            (
                command_type = 'REPLAY'
                AND outcome IN (
                    'REPLAYED',
                    'REPLAY_NOT_FOUND',
                    'REPLAY_NOT_CLAIMABLE',
                    'REPLAY_FAILED',
                    'REPLAY_UNRESOLVED',
                    'INTERNAL_FAILURE'
                )
            )
            OR
            (
                command_type = 'DISCARD'
                AND outcome IN (
                    'DISCARDED',
                    'ALREADY_DISCARDED',
                    'DISCARD_NOT_FOUND',
                    'DISCARD_NOT_DISCARDABLE',
                    'INTERNAL_FAILURE'
                )
            )
        ),

    CONSTRAINT chk_kafka_dead_letter_command_audits_outcome_error
        CHECK (
            (
                outcome IS NULL
                AND error_code IS NULL
            )
            OR
            (
                outcome IN (
                    'REPLAYED',
                    'DISCARDED',
                    'ALREADY_DISCARDED'
                )
                AND error_code IS NULL
            )
            OR
            (
                outcome IN (
                    'REPLAY_NOT_FOUND',
                    'DISCARD_NOT_FOUND'
                )
                AND error_code =
                    'KAFKA_DEAD_LETTER_RECORD_NOT_FOUND'
            )
            OR
            (
                outcome = 'REPLAY_NOT_CLAIMABLE'
                AND error_code =
                    'KAFKA_DEAD_LETTER_RECORD_NOT_CLAIMABLE'
            )
            OR
            (
                outcome = 'REPLAY_FAILED'
                AND error_code =
                    'KAFKA_DEAD_LETTER_REPLAY_FAILED'
            )
            OR
            (
                outcome = 'REPLAY_UNRESOLVED'
                AND error_code =
                    'KAFKA_DEAD_LETTER_REPLAY_UNRESOLVED'
            )
            OR
            (
                outcome = 'DISCARD_NOT_DISCARDABLE'
                AND error_code =
                    'KAFKA_DEAD_LETTER_RECORD_NOT_DISCARDABLE'
            )
            OR
            (
                outcome = 'INTERNAL_FAILURE'
                AND error_code =
                    'KAFKA_DEAD_LETTER_COMMAND_INTERNAL_FAILURE'
            )
        )
);

CREATE INDEX idx_kafka_dead_letter_command_audits_operator_time
    ON kafka_dead_letter_command_audits (
        operator_id,
        occurred_at DESC
    );

CREATE INDEX idx_kafka_dead_letter_command_audits_record_time
    ON kafka_dead_letter_command_audits (
        dead_letter_record_id,
        occurred_at DESC
    );

CREATE INDEX idx_kafka_dead_letter_command_audits_type_time
    ON kafka_dead_letter_command_audits (
        command_type,
        occurred_at DESC
    );

CREATE INDEX idx_kafka_dead_letter_command_audits_occurred_at
    ON kafka_dead_letter_command_audits (
        occurred_at DESC
    );

CREATE FUNCTION reject_kafka_dead_letter_command_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'kafka_dead_letter_command_audits is append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_kafka_dead_letter_command_audits_append_only
    BEFORE UPDATE OR DELETE
    ON kafka_dead_letter_command_audits
    FOR EACH ROW
    EXECUTE FUNCTION
        reject_kafka_dead_letter_command_audit_mutation();
