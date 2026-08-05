ALTER TABLE users
    ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Accounts created before V15 keep their existing authentication eligibility.
UPDATE users
SET email_verified_at = CURRENT_TIMESTAMP;

ALTER TABLE users
    ADD CONSTRAINT chk_users_email_verification_time
    CHECK (
        email_verified_at IS NULL
        OR email_verified_at >= created_at
    );

CREATE TABLE account_action_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    credential_digest BYTEA NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,

    CONSTRAINT fk_account_action_credentials_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_account_action_credentials_digest
        UNIQUE (credential_digest),

    CONSTRAINT chk_account_action_credentials_purpose
        CHECK (
            purpose IN (
                'EMAIL_VERIFICATION',
                'PASSWORD_RECOVERY'
            )
        ),

    CONSTRAINT chk_account_action_credentials_digest_length
        CHECK (
            octet_length(credential_digest) = 32
        ),

    CONSTRAINT chk_account_action_credentials_lifetime
        CHECK (
            expires_at > issued_at
        ),

    CONSTRAINT chk_account_action_credentials_terminal_state
        CHECK (
            consumed_at IS NULL
            OR superseded_at IS NULL
        ),

    CONSTRAINT chk_account_action_credentials_consumption_time
        CHECK (
            consumed_at IS NULL
            OR (
                consumed_at >= issued_at
                AND consumed_at < expires_at
            )
        ),

    CONSTRAINT chk_account_action_credentials_supersession_time
        CHECK (
            superseded_at IS NULL
            OR superseded_at >= issued_at
        )
);

-- Issuance supersedes any prior unresolved credential before inserting the
-- replacement. The index makes concurrent issuance fail safely.
CREATE UNIQUE INDEX uq_account_action_credentials_unresolved
    ON account_action_credentials (
        user_id,
        purpose
    )
    WHERE consumed_at IS NULL
      AND superseded_at IS NULL;

CREATE INDEX idx_account_action_credentials_user_purpose_issued
    ON account_action_credentials (
        user_id,
        purpose,
        issued_at DESC
    );

CREATE INDEX idx_account_action_credentials_expires_at
    ON account_action_credentials (
        expires_at
    );
