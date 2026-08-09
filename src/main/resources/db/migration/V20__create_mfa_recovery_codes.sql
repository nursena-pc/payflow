CREATE TABLE mfa_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    code_digest BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_mfa_recovery_codes_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_mfa_recovery_codes_digest_length
        CHECK (octet_length(code_digest) = 32),
    CONSTRAINT chk_mfa_recovery_codes_consumed_at
        CHECK (consumed_at IS NULL OR consumed_at >= created_at),
    CONSTRAINT uq_mfa_recovery_codes_digest
        UNIQUE (code_digest)
);

CREATE INDEX ix_mfa_recovery_codes_user_unconsumed
    ON mfa_recovery_codes (user_id)
    WHERE consumed_at IS NULL;
