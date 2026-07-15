# ADR 0002: Record transfers with a double-entry ledger

- Status: Accepted and implemented
- Date: 2026-07-12
- Last updated: 2026-07-15

## Context

Updating only a mutable wallet balance does not provide a durable financial history or a strong audit trail.

A wallet balance represents the current state, but it does not independently explain:

- why the balance changed
- which transaction caused the movement
- which wallet received or supplied the value
- whether the movement was recorded symmetrically

Financial movements therefore require immutable accounting records in addition to current wallet balances.

## Decision

Each completed wallet-to-wallet transfer creates two balanced ledger entries:

- one `DEBIT` entry for the source wallet
- one `CREDIT` entry for the target wallet

Both entries:

- reference the same payment transaction
- carry the same monetary amount
- use the same currency
- reference different wallets

Wallet balances are treated as a current-state projection. Ledger entries provide the durable explanation for how those balances changed.

Wallet mutation, payment-transaction persistence, and ledger persistence execute in the same PostgreSQL transaction.

## Domain model

The ledger module contains an immutable ledger-entry model and a `DoubleEntryLedger` domain model.

`DoubleEntryLedger` validates that:

- exactly two entries are supplied
- one entry is a debit
- one entry is a credit
- both entries reference the same payment transaction
- entries reference different wallets
- amounts are equal
- currencies are equal

The domain model reconstructs debit and credit entries by their entry type rather than depending on collection order.

## Invariants

- Ledger-entry amounts must be positive.
- Each completed transfer must produce one debit and one credit entry.
- Debit and credit amounts must be equal.
- Debit and credit currencies must be equal.
- Both entries must reference the same payment transaction.
- Debit and credit entries must reference different wallets.
- A transaction reaches `COMPLETED` only after required ledger entries are persisted.
- A transaction, wallet, and entry-type combination must be unique.
- Application use cases do not update or delete historical ledger entries.

## Database enforcement

PostgreSQL reinforces the domain rules through constraints.

Current persistence guarantees include:

- ledger entry type is restricted to `DEBIT` or `CREDIT`
- entry amount must be positive
- transaction, wallet, and entry-type combinations are unique
- payment-transaction source and target wallets must be different

Database constraints do not replace domain validation. They provide a final integrity boundary against programming errors and concurrent writes.

## Transaction behavior

Ledger persistence participates in the same Spring-managed PostgreSQL transaction as:

- source-wallet debit
- target-wallet credit
- payment-transaction creation
- payment-transaction completion

When ledger persistence fails, the complete transaction rolls back.

The rollback includes:

- wallet balance mutations
- wallet optimistic-lock version changes
- payment-transaction rows
- ledger-entry rows

## Verification

The decision is verified through:

- ledger domain-model unit tests
- ledger persistence-adapter tests
- PostgreSQL constraint tests
- transfer application-service tests
- successful transfer integration tests
- authenticated HTTP integration tests
- forced ledger-failure rollback tests
- concurrent duplicate-transfer tests

The integration suite verifies that a successful transfer creates:

```text
1 COMPLETED payment transaction
1 DEBIT ledger entry
1 CREDIT ledger entry
