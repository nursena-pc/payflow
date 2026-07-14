ALTER TABLE wallets
    RENAME CONSTRAINT wallets_owner_id_key
    TO uq_wallets_owner_id;
