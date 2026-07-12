# ADR 0002: Record transfers with a double-entry ledger

- Status: Accepted
- Date: 2026-07-12

## Context

Updating a mutable wallet balance alone does not provide a durable financial history or a strong audit trail.

## Decision

Each completed transfer will create balanced ledger entries:

- one debit entry for the source wallet
- one credit entry for the target wallet

Ledger entries are immutable. Wallet balances are treated as a performance-oriented projection that must remain consistent with the ledger.

## Invariants

- Entry amounts must be positive.
- Debit and credit totals for a transaction must be equal.
- Entries cannot be updated or deleted through application use cases.
- A transaction reaches `COMPLETED` only after all required entries are persisted.
