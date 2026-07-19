# ADR 0004: Publish transfer events through a transactional outbox

- Status: Accepted
- Date: 2026-07-17

## Context

A completed wallet transfer must eventually notify downstream processes such as audit, notification, analytics, and reporting consumers.

Publishing directly to Kafka from the transfer use case creates a dual-write problem because PostgreSQL and Kafka do not participate in the same local transaction.

Two inconsistent outcomes are possible:

1. PostgreSQL commits the transfer, but Kafka publication fails.
2. Kafka receives the event, but the PostgreSQL transaction rolls back.

In the first case, a real transfer exists without a corresponding event.

In the second case, consumers receive an event for a transfer that does not exist.

The transfer workflow already persists the following state in one Spring-managed PostgreSQL transaction:

- source-wallet debit
- target-wallet credit
- payment transaction
- double-entry ledger records
- completed transaction status

Event persistence must participate in the same transaction without coupling the application use case directly to Kafka.

## Decision

A completed wallet transfer records a versioned integration event in a PostgreSQL transactional outbox.

The outbox record is persisted in the same PostgreSQL transaction as:

- source-wallet debit
- target-wallet credit
- payment-transaction persistence
- double-entry ledger persistence
- transaction completion

The transfer use case does not publish directly to Kafka.

A separate scheduled publisher reads eligible outbox records after the transfer transaction commits and publishes them to Kafka.

The initial topic is:

`wallet.transfer.completed`

The initial event version is:

`1`

This design provides atomic persistence between financial state and the intent to publish an event.

## Application boundary

The transaction application layer depends on a semantic output port for recording the completed-transfer event.

The application layer does not depend on:

- `KafkaTemplate`
- Kafka producer APIs
- Kafka serializers
- Kafka broker availability

The output-port adapter persists the event as an outbox record.

Kafka publication belongs to a separate infrastructure component.

This preserves the hexagonal architecture boundary and keeps the transfer use case independent from the messaging technology.

## Transfer behavior

A new successfully completed transfer creates exactly one outbox event.

The event is recorded only after:

- wallet mutations are valid
- the payment transaction is persisted
- ledger entries are persisted
- the payment transaction reaches `COMPLETED`

The outbox insert still occurs before the surrounding PostgreSQL transaction commits.

When outbox persistence fails, the complete transfer transaction rolls back.

The rollback includes:

- wallet balance changes
- wallet optimistic-lock version changes
- payment-transaction records
- ledger-entry records
- outbox records

## Idempotency replay

A completed idempotency replay returns the previously created transaction result.

A replay does not:

- mutate wallet balances
- create another payment transaction
- create additional ledger entries
- create another outbox event

The original transfer owns the single authoritative completed-transfer event.

A deterministic deduplication key is stored with the outbox record to provide an additional database integrity boundary.

The completed-transfer deduplication key is derived from:

- event type
- event version
- payment transaction identifier

Example:

`wallet.transfer.completed:1:{transactionId}`

PostgreSQL enforces uniqueness for this value.

## Event contract

The version-one event envelope contains:

```json
{
  "eventId": "5da104cf-ce77-4789-a04c-cae91a25588a",
  "eventType": "wallet.transfer.completed",
  "eventVersion": 1,
  "occurredAt": "2026-07-17T16:54:07.584464Z",
  "transactionId": "d48b8f93-fb33-41d6-a494-f5ed03c45928",
  "sourceWalletId": "50c49117-4460-40bf-bef1-223ef8abbefa",
  "targetWalletId": "44abbcc8-cb03-4e6e-90ae-d72221b15606",
  "amount": "125.50",
  "currency": "TRY"
}
```

The amount is represented as a decimal string to avoid binary floating-point precision differences between consumer technologies.

The event does not expose:

- HTTP idempotency keys
- JWT information
- passwords or credentials
- user email addresses
- internal persistence entities

The event contract contains only the information required by downstream consumers.

## Event identity and versioning

`eventId` uniquely identifies one stored event and remains unchanged across publication retries.

`transactionId` identifies the payment transaction represented by the event.

`eventVersion` identifies the event-schema version.

Backward-compatible additions may remain in the current version.

Breaking contract changes require a new event version.

Consumers must explicitly handle the versions they support.

## Outbox persistence model

The outbox table stores event identity, routing information, immutable payload, and mutable delivery metadata.

The initial model contains:

- `id`
- `aggregate_type`
- `aggregate_id`
- `event_type`
- `event_version`
- `topic`
- `partition_key`
- `deduplication_key`
- `payload`
- `status`
- `attempt_count`
- `available_at`
- `locked_at`
- `locked_until`
- `locked_by`
- `created_at`
- `published_at`
- `last_error`

The payload is stored as PostgreSQL `JSONB`.

Event identity, routing information, and payload fields are immutable after insertion.

Only delivery-related metadata may change during publication.

Supported statuses are:

- `PENDING`
- `PROCESSING`
- `PUBLISHED`
- `FAILED`

PostgreSQL constraints enforce:

- valid status values
- positive event versions
- non-negative attempt counts
- unique event identifiers
- unique deduplication keys
- required routing information
- required payload values

## Publisher behavior

A scheduled publisher periodically claims eligible records in bounded batches.

Eligible records include:

- `PENDING` records whose `available_at` time has arrived
- `PROCESSING` records whose publication lease has expired

Rows are claimed using PostgreSQL locking equivalent to:

`FOR UPDATE SKIP LOCKED`

The claim operation:

1. selects an eligible batch
2. marks the records as `PROCESSING`
3. records the publisher identity
4. records the lease expiration time
5. commits the claim transaction

Kafka publication occurs after the claim transaction commits.

This prevents database locks from being held while waiting for Kafka network operations.

Multiple publisher instances may run concurrently without intentionally claiming the same active record.

An expired lease makes abandoned records eligible for recovery after an application crash.

## Publication success

Kafka publication is considered successful only after the broker acknowledges the send operation.

After acknowledgement, the publisher marks the outbox record as:

- `PUBLISHED`
- populated `published_at`
- cleared lock metadata

Published records are not selected again during normal polling.

## Retry and failure behavior

When Kafka publication fails:

- `attempt_count` is incremented
- a sanitized failure description is recorded
- lock metadata is cleared
- the next `available_at` time is calculated using bounded exponential backoff

After a configurable maximum number of attempts, the record becomes `FAILED`.

Failed records remain in PostgreSQL for inspection and controlled recovery.

A failed publication does not roll back or invalidate the completed financial transfer.

## Delivery guarantee

The system provides at-least-once event delivery.

Exactly-once delivery is not claimed.

A crash may occur after Kafka acknowledges an event but before PostgreSQL records the event as published.

In that situation, the same `eventId` may be published again after lease recovery.

Every consumer must therefore process events idempotently using `eventId`.

Consumer-side idempotency is a required part of the messaging contract.

## Kafka key and ordering

The payment transaction identifier is used as the Kafka message key.

Retries of the same event therefore use the same key.

The system does not promise global transfer ordering.

Consumers must not depend on a total order across unrelated wallet transfers.

## Retention

Published and failed outbox records are retained initially for operational inspection and auditability.

Automatic archival or deletion is deferred until retention requirements are defined.

A future cleanup process must never remove active `PENDING` or `PROCESSING` records.

## Observability

The publisher exposes structured operational information for:

- claimed event count
- successful publication count
- failed publication count
- retry count
- permanently failed event count
- publication latency
- oldest pending-event age

Logs include event identifiers and transaction identifiers but do not expose credentials or sensitive payload data.

## Consequences

### Positive consequences

- Financial state and event intent are persisted atomically.
- Kafka outages do not lose completed-transfer events.
- Transfer processing does not require Kafka availability.
- Application services remain independent from Kafka APIs.
- Publication retries are durable.
- Multiple publisher instances can operate safely.
- Event contracts are explicitly versioned.
- Duplicate publication is manageable through event identity.
- Failed events remain inspectable.

### Trade-offs

- Event publication is asynchronous rather than immediate.
- PostgreSQL receives additional writes and polling queries.
- Outbox records require retention management.
- Delivery metadata and lease recovery increase implementation complexity.
- Consumers must implement idempotent processing.
- At-least-once delivery permits duplicate Kafka messages.
- Monitoring is required for pending and failed events.

## Alternatives considered

### Publish directly to Kafka inside the transfer use case

Rejected because PostgreSQL and Kafka cannot be committed atomically through the current local transaction.

This approach could lose events or publish events for rolled-back transfers.

### Publish through an after-commit application listener

Rejected as the durability mechanism.

An application crash after PostgreSQL commit but before listener publication could permanently lose the event.

An after-commit listener may trigger work, but it cannot replace durable outbox persistence.

### Distributed XA transaction

Rejected because it introduces significant operational and implementation complexity.

It would also couple transfer availability to the messaging infrastructure.

### Change-data capture

A CDC platform such as Debezium could publish outbox rows without an application polling publisher.

This remains a possible future evolution, but it is not selected initially because it adds infrastructure and operational complexity beyond the current project requirements.

## Verification

The decision will be verified through:

- outbox event-model unit tests
- event serialization contract tests
- PostgreSQL outbox persistence tests
- PostgreSQL constraint tests
- successful-transfer integration tests
- forced outbox-persistence rollback tests
- completed idempotency-replay tests
- concurrent duplicate-transfer tests
- publisher claim and lease-recovery tests
- successful Kafka-publication tests
- Kafka-unavailable retry tests
- concurrent publisher tests
- consumer idempotency tests
- Testcontainers PostgreSQL and Kafka integration tests

A successful new transfer must result in:

```text
1 COMPLETED payment transaction
1 DEBIT ledger entry
1 CREDIT ledger entry
1 PENDING completed-transfer outbox event
```

A completed idempotency replay must leave those counts unchanged.
