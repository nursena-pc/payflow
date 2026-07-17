ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_transactions_distinct_wallets
        CHECK (
            source_wallet_id IS NULL
            OR target_wallet_id IS NULL
            OR source_wallet_id <> target_wallet_id
        );

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_entries_entry_type
        CHECK (entry_type IN ('DEBIT', 'CREDIT'));

ALTER TABLE ledger_entries
    ADD CONSTRAINT uq_ledger_entries_transaction_wallet_type
        UNIQUE (
            transaction_id,
            wallet_id,
            entry_type
        );
