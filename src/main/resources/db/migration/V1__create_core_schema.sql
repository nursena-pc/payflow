CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL UNIQUE REFERENCES users(id),
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    source_wallet_id UUID REFERENCES wallets(id),
    target_wallet_id UUID REFERENCES wallets(id),
    transaction_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(100),
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_transaction_idempotency UNIQUE (source_wallet_id, idempotency_key)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES payment_transactions(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_transactions_source_created_at
    ON payment_transactions(source_wallet_id, created_at DESC);

CREATE INDEX idx_transactions_target_created_at
    ON payment_transactions(target_wallet_id, created_at DESC);

CREATE INDEX idx_transactions_status_created_at
    ON payment_transactions(status, created_at DESC);

CREATE INDEX idx_ledger_transaction_id
    ON ledger_entries(transaction_id);

CREATE INDEX idx_ledger_wallet_created_at
    ON ledger_entries(wallet_id, created_at DESC);
