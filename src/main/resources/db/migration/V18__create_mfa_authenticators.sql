CREATE TABLE mfa_authenticators (
    user_id UUID PRIMARY KEY,
    state VARCHAR(16) NOT NULL,
    protected_secret BYTEA NOT NULL,
    enrollment_expires_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_mfa_authenticators_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_mfa_authenticators_state
        CHECK (state IN ('PENDING', 'ENABLED')),
    CONSTRAINT ck_mfa_authenticators_protected_secret
        CHECK (octet_length(protected_secret) >= 49),
    CONSTRAINT ck_mfa_authenticators_timestamps
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_mfa_authenticators_lifecycle
        CHECK (
            (
                state = 'PENDING'
                AND enrollment_expires_at IS NOT NULL
                AND enrollment_expires_at > created_at
                AND activated_at IS NULL
            )
            OR
            (
                state = 'ENABLED'
                AND enrollment_expires_at IS NULL
                AND activated_at IS NOT NULL
                AND activated_at >= created_at
                AND updated_at >= activated_at
            )
        )
);
