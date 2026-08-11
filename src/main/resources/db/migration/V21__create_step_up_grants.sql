CREATE TABLE step_up_grants (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    grant_digest BYTEA NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    superseded_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_step_up_grants_subject
        FOREIGN KEY (subject_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_step_up_grants_purpose
        CHECK (purpose IN (
            'mfa-disable',
            'recovery-code-rotation',
            'mfa-authenticator-replacement',
            'kafka-dead-letter-replay',
            'kafka-dead-letter-discard'
        )),
    CONSTRAINT chk_step_up_grants_digest_length
        CHECK (octet_length(grant_digest) = 32),
    CONSTRAINT chk_step_up_grants_lifetime
        CHECK (expires_at > issued_at),
    CONSTRAINT chk_step_up_grants_consumed_at
        CHECK (
            consumed_at IS NULL
            OR (consumed_at >= issued_at AND consumed_at <= expires_at)
        ),
    CONSTRAINT chk_step_up_grants_superseded_at
        CHECK (
            superseded_at IS NULL
            OR superseded_at >= issued_at
        ),
    CONSTRAINT chk_step_up_grants_terminal_state
        CHECK (consumed_at IS NULL OR superseded_at IS NULL),
    CONSTRAINT uq_step_up_grants_digest
        UNIQUE (grant_digest)
);

CREATE INDEX ix_step_up_grants_subject_purpose_unconsumed
    ON step_up_grants (subject_id, purpose, expires_at)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;
