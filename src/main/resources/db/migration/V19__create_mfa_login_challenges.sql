CREATE TABLE mfa_login_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    challenge_digest BYTEA NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts_remaining INTEGER NOT NULL,
    state VARCHAR(16) NOT NULL,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT fk_mfa_login_challenges_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_mfa_login_challenges_digest UNIQUE (challenge_digest),
    CONSTRAINT ck_mfa_login_challenges_digest CHECK (octet_length(challenge_digest) = 32),
    CONSTRAINT ck_mfa_login_challenges_lifetime CHECK (expires_at > issued_at),
    CONSTRAINT ck_mfa_login_challenges_attempts CHECK (attempts_remaining BETWEEN 0 AND 10),
    CONSTRAINT ck_mfa_login_challenges_state CHECK (
        state IN ('PENDING', 'CONSUMED', 'EXHAUSTED', 'EXPIRED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_mfa_login_challenges_terminal CHECK (
        (state = 'PENDING' AND attempts_remaining > 0 AND resolved_at IS NULL)
        OR
        (state <> 'PENDING' AND resolved_at IS NOT NULL AND resolved_at >= issued_at)
    ),
    CONSTRAINT ck_mfa_login_challenges_exhausted CHECK (
        state <> 'EXHAUSTED' OR attempts_remaining = 0
    )
);

CREATE UNIQUE INDEX uq_mfa_login_challenges_pending_user
    ON mfa_login_challenges (user_id)
    WHERE state = 'PENDING';

CREATE INDEX ix_mfa_login_challenges_user_issued
    ON mfa_login_challenges (user_id, issued_at DESC);
