# ADR 0005: Control Kafka dead-letter replay through durable PostgreSQL state

- Status: Accepted
- Date: 2026-07-21

> Current-state note (v1.0.0 release-candidate): this ADR preserves the original replay-foundation decision. Authorized replay/discard HTTP operations, append-only command auditing, and audit query endpoints are now implemented under the `PAYFLOW_OPERATIONS` boundary. The historical decision body below is retained unchanged; see ADR 0006 through ADR 0008 and `docs/v1-financial-messaging-integrity.md` for the current integrated contract.

## Context

The transfer-completed Kafka consumer uses bounded retries and publishes permanent or exhausted failures to:

`wallet.transfer.completed.dlt`

Dead-letter publication prevents an unrecoverable record from blocking the source consumer partition indefinitely.

However, routing a record to a dead-letter topic does not complete the operational lifecycle.

Operators still need to:

- inspect failed records
- understand their original Kafka location
- distinguish malformed records from transient processing failures
- decide whether a record is safe to replay
- limit repeated replay attempts
- prevent uncontrolled source-to-DLT replay loops
- preserve an auditable history of replay decisions

Automatically forwarding every DLT record back to the source topic is unsafe.

Permanent failures such as malformed JSON or an invalid partition key would repeatedly travel through:

`source topic -> consumer -> DLT -> source topic`

Kafka offsets alone are also insufficient as the operational source of truth because retention policies may eventually remove DLT records.

A durable PostgreSQL representation is required before controlled replay is introduced.

## Decision

PayFlow persists transfer-completed dead-letter records in PostgreSQL before they become eligible for replay.

A dedicated Kafka listener consumes the configured DLT topic and records each DLT message through the event-processing application boundary.

The DLT listener does not automatically republish records.

Replay requires an explicit application command.

The initial replay workflow remains internal until PayFlow has an authenticated operations or administrator authorization model.

No unauthenticated HTTP replay endpoint will be exposed.

## Architecture boundary

The Kafka input adapter is responsible for:

- reading the DLT `ConsumerRecord`
- extracting required Spring Kafka dead-letter headers
- validating Kafka metadata
- creating an application command

The application service is responsible for:

- creating the dead-letter domain model
- assigning initial lifecycle state
- recording the reception timestamp
- enforcing application-level replay rules

The persistence output port is responsible for durable storage.

The application layer does not depend directly on:

- `KafkaTemplate`
- Kafka consumer APIs
- JDBC
- Spring Kafka header constants

Kafka-specific header extraction stays inside the Kafka input adapter.

JDBC-specific persistence stays inside the persistence output adapter.

## Dead-letter identity

A dead-letter record is uniquely identified by its physical location in the DLT:

- DLT topic
- DLT partition
- DLT offset

PostgreSQL enforces uniqueness for:

`(dlt_topic, dlt_partition, dlt_offset)`

This identity makes DLT intake idempotent.

When the DLT listener receives the same Kafka record more than once, the existing database row remains authoritative and no duplicate dead-letter record is created.

The event identifier inside the payload is not used as the intake identity because malformed payloads may not contain a readable event identifier.

## Persisted information

The initial dead-letter model stores:

- generated record identifier
- DLT topic
- DLT partition
- DLT offset
- original topic
- original partition
- original offset
- original consumer group
- original record key
- original payload
- original exception type
- original exception message
- lifecycle status
- replay count
- reception timestamp
- last replay timestamp
- replay lease owner
- replay lease expiration
- last replay error
- replay origin identifier
- replay-attempt base

The original record key and payload may be null.

Malformed or incomplete records must still remain inspectable even when they are not replayable.

Full exception stack traces are not stored.

Stack traces may contain excessive or sensitive implementation details and are not required to decide whether a record can be replayed.

## Lifecycle

Supported statuses are:

- `RECEIVED`
- `REPLAYING`
- `REPLAYED`
- `REPLAY_FAILED`
- `DISCARDED`

### RECEIVED

The record was durably ingested from the DLT and has not been replayed directly from its current physical DLT location.

A `RECEIVED` record may still be replay-derived when its lineage metadata indicates that replay attempts occurred before the current physical DLT record was created.

### REPLAYING

A replay worker has atomically claimed the record.

The claim uses a bounded lease so that a crashed replay worker does not leave the record permanently locked.

### REPLAYED

The record was published successfully to its original topic and the successful result was persisted.

### REPLAY_FAILED

The latest replay publication or state transition failed.

The record may become eligible for another explicit replay attempt when the configured replay limit has not been reached.

### DISCARDED

An operator determined that the record must not be replayed.

This is a terminal operational decision.

## Replay eligibility

A record is replayable only when:

- its status is `RECEIVED` or `REPLAY_FAILED`
- `replay_attempt_base + replay_count` is lower than the configured maximum replay-attempt limit
- it is not protected by an active replay lease
- its original topic is present
- its payload satisfies the replay command requirements
- its original topic is not the configured DLT topic

The application must reject replay when the original topic equals the DLT topic.

This prevents direct DLT-to-DLT loops.

A malformed payload may be stored successfully while remaining ineligible for replay until an operator corrects the underlying issue through a future remediation workflow.

The initial implementation does not mutate stored payloads.

## Replay claim

Replay uses an atomic PostgreSQL claim operation.

A successful claim:

- changes the status to `REPLAYING`
- increments the replay count
- records the lease owner
- records the lease expiration
- clears the previous replay error

Concurrent workers cannot claim the same record simultaneously.

Expired `REPLAYING` leases may be reclaimed.

A replay count is incremented when a replay attempt is claimed, not after publication succeeds.

This preserves an accurate upper bound even when a worker crashes during publication.

`last_replayed_at` records the start time of the latest replay attempt, meaning the moment the record was claimed. It does not prove that Kafka publication completed successfully.

## Kafka publication

A claimed record is published to its original topic.

The replay publication uses:

- original topic
- original key
- original payload
- PayFlow replay-lineage headers

Spring Kafka DLT exception headers are not copied to the replayed source record.

Delivery-attempt and prior DLT metadata headers are also not copied.

This prevents dead-letter and exception headers from accumulating across replay cycles.

Business-header replay may be introduced later through an explicit allowlist.

## Delivery semantics

PostgreSQL and Kafka do not participate in one local transaction.

Replay therefore provides at-least-once publication.

The workflow is:

1. claim the dead-letter record in PostgreSQL
2. publish the original key and payload to Kafka
3. mark the dead-letter record as `REPLAYED`

If publication fails, the record is marked `REPLAY_FAILED` when possible.

If Kafka publication succeeds but the database success update fails, the replay lease eventually expires and the record may be published again.

Duplicate replay publication is therefore possible.

The existing transfer-completed consumer protects its database side effects using:

- stable logical consumer name
- event identifier

A duplicate valid event is processed as a successful no-op.

This existing idempotency boundary is required for safe at-least-once replay.

## Replay limits

Replay attempts are bounded by configuration.

The limit applies to the complete logical replay chain rather than independently to each physical Kafka DLT record.

A record that reaches the configured chain-wide replay-attempt limit is no longer eligible for replay without an explicit future administrative intervention.

Replay limits prevent permanent failures from consuming resources indefinitely.

### Replay lineage and chain-wide attempt limits

A replay attempt limit must apply to the complete logical replay chain, not only to one physical Kafka DLT record.

Each persisted dead-letter record therefore stores:

- `replay_origin_id`: the identifier of the first persisted dead-letter record in the replay chain.
- `replay_attempt_base`: the number of replay attempts that occurred before the current physical DLT record was created.
- `replay_count`: the number of attempts claimed directly from the current physical DLT record.

The total number of attempts for a record is:

`replay_attempt_base + replay_count`

A record may be claimed only while this total is lower than the configured maximum replay-attempt limit.

An initial DLT record uses its own identifier as `replay_origin_id` and stores a zero `replay_attempt_base`.

A replay-derived DLT record preserves the original `replay_origin_id` and receives the total attempt number from the replayed Kafka message.

### Replay lineage headers

A replay publication adds exactly these PayFlow-owned headers:

- `payflow-replay-origin-id`
- `payflow-replay-attempt`

`payflow-replay-attempt` contains the total attempt count after the current record has been claimed:

`replay_attempt_base + replay_count`

The replay publisher must not copy Spring Kafka DLT exception, stack-trace, delivery-attempt, original-topic, original-offset, or original-consumer-group headers onto the replayed source message.

The DLT intake listener applies the following rules:

- Neither replay header present: initial DLT delivery.
- Both replay headers present and valid: replay-derived delivery.
- Only one header present: reject the record.
- Invalid UUID, non-integer attempt, or non-positive attempt: reject the record.

Rejected lineage metadata is handled fail-closed: the intake transaction is not committed and the DLT consumer offset must not advance.

## Offset behavior

The DLT intake listener acknowledges a Kafka record only after its PostgreSQL representation is durably committed.

When database persistence fails, the listener must not silently advance the DLT consumer offset.

Duplicate intake caused by Kafka redelivery is expected and is handled through the unique DLT-location constraint.

Invalid or incomplete replay-lineage metadata must also prevent successful acknowledgement.

The DLT intake listener must not advance its consumer offset after rejecting lineage metadata.

## Security

Replay is an operationally privileged action.

The initial persistence and intake implementation exposes no replay HTTP endpoint.

A future HTTP or administrative adapter must require an explicit operations authority.

Authenticated end-user permissions are not sufficient for replay access.

Replay actions must be attributable to an operational actor when an authenticated operations boundary is introduced.

## Observability

The replay lifecycle will expose low-cardinality metrics for:

- DLT records received
- duplicate DLT deliveries
- replay claims
- successful replay publications
- failed replay publications
- discarded records
- records blocked by the replay limit

Identifiers, payloads, offsets, exception messages, and record keys will not be metric tags.

Replay-chain identifiers and total replay-attempt values must also not be exposed as metric tags because they would introduce high-cardinality dimensions.

## Database constraints

PostgreSQL will enforce:

- unique DLT topic, partition, and offset
- nonblank topic names
- non-negative partitions
- non-negative offsets
- non-negative replay count
- non-negative replay-attempt base
- valid lifecycle statuses
- replay lease consistency
- replay timestamp consistency
- valid initial and replay-derived lineage relationships
- non-overflowing total replay-attempt count

Database constraints complement domain validation and protect the operational state from invalid writes outside the application service.

## Consequences

### Positive

- DLT records remain inspectable beyond Kafka retention.
- DLT intake is idempotent.
- Replay is explicit rather than automatic.
- Replay attempts are bounded across the complete logical replay chain.
- Replay-derived DLT records preserve their original lineage.
- Concurrent replay claims can be controlled.
- Kafka exception headers do not accumulate.
- Invalid lineage metadata is rejected without advancing the DLT consumer offset.
- Existing consumer idempotency makes replay duplicates safe.
- Persistence and Kafka concerns remain behind hexagonal ports.

### Negative

- PostgreSQL becomes part of the DLT operational workflow.
- Replay cannot provide exactly-once publication across PostgreSQL and Kafka.
- Additional lifecycle recovery logic is required for expired replay leases.
- Replay lineage must be propagated correctly across Kafka publication and DLT intake.
- Operational authorization must be designed before exposing replay over HTTP.
- Stored payloads may contain business data and require normal database access controls.

## Alternatives considered

### Automatically republish every DLT record

Rejected because permanent failures would create unbounded source-to-DLT loops.

### Apply the replay limit independently to each physical DLT record

Rejected because every source-to-DLT cycle would create a new physical record whose local replay count started from zero.

A per-record limit would therefore fail to bound the complete logical replay chain.

### Use Kafka offsets as the only replay state

Rejected because Kafka retention removes historical records and offsets do not provide operational lifecycle, replay limits, lineage, or discard decisions.

### Publish and update PostgreSQL atomically

Rejected because PostgreSQL and Kafka do not share a local transaction and introducing distributed transactions would add disproportionate complexity.

### Use the event identifier as the DLT intake key

Rejected because malformed records may not contain a readable event identifier.

### Copy every DLT header during replay

Rejected because exception, stack-trace, delivery-attempt, and prior DLT headers would accumulate and leak infrastructure metadata back into the source flow.

## Verification

The implementation must demonstrate:

- duplicate delivery of one DLT offset creates one database record
- malformed payloads can still be persisted
- missing required original-record headers are rejected
- initial DLT records use their own identifier as `replay_origin_id`
- initial DLT records use a zero `replay_attempt_base`
- replay-derived records preserve the original replay-chain identifier
- replay-derived records persist the attempt base received from Kafka
- partial replay-lineage headers are rejected
- invalid replay-origin UUID values are rejected
- non-integer or non-positive replay-attempt values are rejected
- rejected lineage metadata prevents DLT offset advancement
- persistence failure prevents successful DLT consumption
- concurrent replay claims cannot own the same record
- expired replay leases can be reclaimed
- chain-wide replay limits are enforced using `replay_attempt_base + replay_count`
- total replay-attempt arithmetic cannot overflow
- successful publication transitions the record to `REPLAYED`
- failed publication transitions the record to `REPLAY_FAILED`
- replayed valid events remain safe under duplicate publication
