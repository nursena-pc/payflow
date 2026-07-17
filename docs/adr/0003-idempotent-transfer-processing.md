# ADR 0003: Enforce transfer idempotency with PostgreSQL

- Status: Accepted and implemented
- Date: 2026-07-15

## Context

A client may repeat a transfer request because of network timeouts, connection interruption, application restarts, delayed responses, or uncertainty about whether the first request completed.

Treating every retry as a new request could create duplicate financial movements.

An application-level lookup alone is insufficient. Two concurrent requests may both observe that no transaction exists before either request inserts its transaction row.

## Decision

Every transfer request requires an `Idempotency-Key` HTTP header.

The key is normalized and stored with the payment transaction. PostgreSQL enforces uniqueness for the combination:

`source_wallet_id + idempotency_key`

The source-wallet scope allows unrelated users to use the same client-generated key without conflicting.

The database constraint remains the final authority for concurrent races.

## Request evaluation

The application searches for an existing payment transaction using the authenticated source wallet and normalized idempotency key.

### No existing transaction

A new transfer may begin.

### Completed transaction with matching payload

The existing transaction result is returned.

The application does not:

- debit the source wallet again
- credit the target wallet again
- create another payment transaction
- create additional ledger entries

### Existing transaction with a different payload

The request is rejected with `IDEMPOTENCY_KEY_CONFLICT`.

A key cannot represent two logically different transfers from the same source wallet.

### Existing unfinished transaction

The request is rejected with `IDEMPOTENCY_REQUEST_IN_PROGRESS`.

An unfinished transaction cannot be returned as a completed replay.

## Payload identity

A replay matches the original request when the following values are equivalent:

- source wallet
- target wallet
- amount
- transfer currency
- normalized idempotency key

The source wallet is derived from the authenticated JWT subject rather than supplied by the client.

## Concurrency behavior

Concurrent requests may both pass the initial application lookup.

PostgreSQL then controls ownership of the unique source-wallet and idempotency-key combination. Only one request can create the authoritative transaction row.

Depending on timing, another caller may:

- observe and replay the completed transaction
- receive a stable idempotency conflict while the competing transaction owns the key

Regardless of the caller outcome, persisted financial state must contain at most:

- one payment transaction
- one source-wallet debit
- one target-wallet credit
- one `DEBIT` ledger entry
- one `CREDIT` ledger entry

## Consequences

### Positive consequences

- Client retries do not duplicate completed transfers.
- Completed replay returns a stable transaction identity.
- Balance mutations are not repeated.
- Ledger entries are not duplicated.
- PostgreSQL remains the source of truth.
- Concurrent races are protected by a database constraint.
- Idempotency correctness does not depend on Redis availability.

### Trade-offs

- Clients must generate and retain stable request keys.
- A key cannot be reused for a different transfer from the same source wallet.
- Concurrent callers may receive a conflict instead of an immediate replay.
- Idempotency records currently share the payment-transaction lifecycle.
- A retention and archival policy will be required later.

## Alternatives considered

### Application lookup without a database constraint

Rejected because concurrent requests could both pass the lookup and create duplicate transfers.

### Redis-only idempotency

Rejected as the source of truth because financial transaction ownership must remain durable in PostgreSQL.

Redis may later support bounded acceleration or temporary metadata, but it will not replace the database guarantee.

### Globally unique idempotency keys

Not selected because independent users should be able to generate identical client-side keys without interfering with each other.

## Verification

The decision is verified through:

- idempotency value-object tests
- application-service replay tests
- conflicting-payload tests
- payment-transaction persistence tests
- PostgreSQL unique-constraint tests
- sequential integration tests
- controlled concurrent integration tests
- authenticated HTTP integration tests