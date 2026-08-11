CREATE TABLE account_security_audits (
    id UUID PRIMARY KEY,
    subject_user_id UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_account_security_audits_subject_user
        FOREIGN KEY (subject_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_account_security_audits_action
        CHECK (action IN ('MFA_DISABLED'))
);

CREATE INDEX ix_account_security_audits_subject_occurred_at
    ON account_security_audits (
        subject_user_id,
        occurred_at DESC
    );
