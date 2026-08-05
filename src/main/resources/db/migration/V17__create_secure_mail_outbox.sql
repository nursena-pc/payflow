CREATE TABLE mail_outbox_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    protected_body BYTEA,
    message_id VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,
    locked_by VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    last_error VARCHAR(1000),

    CONSTRAINT fk_mail_outbox_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_mail_outbox_message_id
        UNIQUE (message_id),

    CONSTRAINT chk_mail_outbox_purpose
        CHECK (
            purpose IN (
                'EMAIL_VERIFICATION',
                'PASSWORD_RECOVERY'
            )
        ),

    CONSTRAINT chk_mail_outbox_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'SENT',
                'FAILED'
            )
        ),

    CONSTRAINT chk_mail_outbox_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT chk_mail_outbox_lifetime
        CHECK (expires_at > created_at),

    CONSTRAINT chk_mail_outbox_availability
        CHECK (available_at >= created_at),

    CONSTRAINT chk_mail_outbox_processing_lock
        CHECK (
            (
                status = 'PROCESSING'
                AND locked_at IS NOT NULL
                AND locked_until IS NOT NULL
                AND locked_by IS NOT NULL
                AND locked_until > locked_at
            )
            OR
            (
                status <> 'PROCESSING'
                AND locked_at IS NULL
                AND locked_until IS NULL
                AND locked_by IS NULL
            )
        ),

    CONSTRAINT chk_mail_outbox_protected_body
        CHECK (
            (
                status IN ('PENDING', 'PROCESSING')
                AND protected_body IS NOT NULL
                AND octet_length(protected_body) > 0
            )
            OR
            (
                status IN ('SENT', 'FAILED')
                AND protected_body IS NULL
            )
        ),

    CONSTRAINT chk_mail_outbox_sent_at
        CHECK (
            (
                status = 'SENT'
                AND sent_at IS NOT NULL
                AND sent_at >= created_at
            )
            OR
            (
                status <> 'SENT'
                AND sent_at IS NULL
            )
        )
);

CREATE INDEX idx_mail_outbox_claim
    ON mail_outbox_messages (
        available_at,
        created_at,
        id
    )
    WHERE status = 'PENDING';

CREATE INDEX idx_mail_outbox_expired_lease
    ON mail_outbox_messages (
        locked_until,
        created_at,
        id
    )
    WHERE status = 'PROCESSING';

CREATE INDEX idx_mail_outbox_unresolved_identity
    ON mail_outbox_messages (
        user_id,
        purpose,
        created_at DESC
    )
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_mail_outbox_expiration
    ON mail_outbox_messages (
        expires_at,
        created_at,
        id
    )
    WHERE status IN ('PENDING', 'PROCESSING');
