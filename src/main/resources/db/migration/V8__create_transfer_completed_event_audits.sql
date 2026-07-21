CREATE TABLE transfer_completed_event_audits (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(200) NOT NULL,
    event_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    transaction_id UUID NOT NULL,
    source_wallet_id UUID NOT NULL,
    target_wallet_id UUID NOT NULL,
    amount NUMERIC NOT NULL,
    currency VARCHAR(3) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_transfer_completed_event_audits_transaction_id
        UNIQUE (transaction_id),

    CONSTRAINT chk_transfer_completed_event_audits_type
        CHECK (
            event_type = 'wallet.transfer.completed'
        ),

    CONSTRAINT chk_transfer_completed_event_audits_version
        CHECK (
            event_version = 1
        ),

    CONSTRAINT chk_transfer_completed_event_audits_wallets
        CHECK (
            source_wallet_id <> target_wallet_id
        ),

    CONSTRAINT chk_transfer_completed_event_audits_amount
        CHECK (
            amount > 0
        ),

    CONSTRAINT chk_transfer_completed_event_audits_currency
        CHECK (
            currency ~ '^[A-Z]{3}$'
        )
);

CREATE INDEX idx_transfer_completed_event_audits_recorded_at
    ON transfer_completed_event_audits (
        recorded_at DESC
    );
