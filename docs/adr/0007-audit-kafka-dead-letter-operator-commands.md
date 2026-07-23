# ADR 0007: Audit authorized Kafka dead-letter commands

- Status: Accepted
- Date: 2026-07-23

## Context

PayFlow exposes authorized operations for replaying and discarding
durably stored Kafka dead-letter records. These operations are protected
by the explicit `PAYFLOW_OPERATIONS` authority.

Replay and discard change operational state. Replay may also publish a
Kafka record again. The system must therefore retain durable evidence of:

- which authenticated operator issued the command
- which dead-letter record was targeted
- whether the command was replay or discard
- when processing started
- how processing completed
- which safe error classification applies when processing does not succeed

HTTP access logs, application logs, and the mutable dead-letter lifecycle
row are not sufficient as the authoritative command history. Logs may be
unavailable or retained for a limited period, while the lifecycle row
represents current state rather than every authorized command attempt.

The audit trail must minimize stored data. It must not contain:

- Kafka payloads
- Kafka record keys
- JWTs or access tokens
- operator email addresses
- exception messages
- stack traces
- replay lease owners

## Decision

PayFlow will persist an append-only PostgreSQL audit trail for authorized
Kafka dead-letter replay and discard commands.

Auditing will be implemented by operator-aware application orchestration
that wraps the existing replay and discard use cases. The existing use
cases remain responsible for their current lifecycle outcomes. The web
adapter retains its existing mapping from application results to HTTP
responses.

The audited flow is:

```text
authorized operations request
        |
verified JWT subject -> operator UUID
        |
operator-aware application command
        |
ATTEMPTED audit append
        |
existing replay or discard use case
        |
COMPLETED audit append
        |
existing web outcome mapping
```

The controller supplies trusted operator identity and the target record
identifier. It does not perform audit persistence or derive audit
outcomes. It continues to map audited application results to the existing
HTTP contract.

The existing replay and discard services do not become aware of JWT,
Spring Security, HTTP, JDBC, or the audit table.

## Operator identity

The web adapter derives `operatorId` from the verified JWT `sub` claim and
parses it as a UUID.

The application command contains only:

- `operatorId`
- `recordId`

The complete JWT and Spring Security types do not cross into the
application layer. Request headers and email addresses are not accepted
as operator identity.

A request whose verified token does not provide a valid UUID subject is
rejected before an audited command use case is invoked. No audit row is
created because a trustworthy operator identifier is unavailable.

## Command identity and stages

Each operator command invocation receives one generated `commandId`.
The same value correlates its two audit rows. Each row also receives its
own generated `id`.

Supported stages are:

- `ATTEMPTED`
- `COMPLETED`

An `ATTEMPTED` row proves that PayFlow durably accepted an authorized
operator command before invoking the existing command use case. It has no
outcome and no error code.

A `COMPLETED` row records the safe application result observed after the
existing command use case returns or throws. It must have an outcome.

PostgreSQL enforces `UNIQUE (command_id, stage)` so one command cannot
persist the same stage more than once.

## Audit data model

Each row contains only:

- `id`
- `commandId`
- `stage`
- `operatorId`
- `deadLetterRecordId`
- `commandType`
- `outcome`
- `errorCode`
- `occurredAt`

Supported command types are:

- `REPLAY`
- `DISCARD`

Timestamps use the shared UTC `Clock` and are truncated to PostgreSQL
microsecond precision before persistence.

## Outcomes and safe error codes

Supported completed outcomes are:

- `REPLAYED`
- `REPLAY_NOT_FOUND`
- `REPLAY_NOT_CLAIMABLE`
- `REPLAY_FAILED`
- `REPLAY_UNRESOLVED`
- `DISCARDED`
- `ALREADY_DISCARDED`
- `DISCARD_NOT_FOUND`
- `DISCARD_NOT_DISCARDABLE`
- `INTERNAL_FAILURE`

Successful outcomes have no error code:

- `REPLAYED`
- `DISCARDED`
- `ALREADY_DISCARDED`

Controlled unsuccessful outcomes map to fixed safe codes:

| Outcome | Safe error code |
|---|---|
| `REPLAY_NOT_FOUND` | `KAFKA_DEAD_LETTER_RECORD_NOT_FOUND` |
| `REPLAY_NOT_CLAIMABLE` | `KAFKA_DEAD_LETTER_RECORD_NOT_CLAIMABLE` |
| `REPLAY_FAILED` | `KAFKA_DEAD_LETTER_REPLAY_FAILED` |
| `REPLAY_UNRESOLVED` | `KAFKA_DEAD_LETTER_REPLAY_UNRESOLVED` |
| `DISCARD_NOT_FOUND` | `KAFKA_DEAD_LETTER_RECORD_NOT_FOUND` |
| `DISCARD_NOT_DISCARDABLE` | `KAFKA_DEAD_LETTER_RECORD_NOT_DISCARDABLE` |
| `INTERNAL_FAILURE` | `KAFKA_DEAD_LETTER_COMMAND_INTERNAL_FAILURE` |

The application model uses explicit enums. A safe error code is derived
from the selected outcome rather than accepted as arbitrary text.

The model rejects:

- an outcome on an `ATTEMPTED` row
- a missing outcome on a `COMPLETED` row
- a replay outcome for a discard command
- a discard outcome for a replay command
- an error-code state inconsistent with the selected outcome

## Transaction boundaries

Audit appends execute through a dedicated Spring bean with
`PROPAGATION_REQUIRES_NEW`.

The transactional component is separate from the audited orchestrator.
The implementation does not rely on same-class self-invocation because
Spring transaction interception would not apply to that call.

Each audit row commits independently from replay or discard processing.
The outer audited orchestration does not open a transaction around the
complete delegate invocation.

### ATTEMPTED persistence failure

Audit availability is a prerequisite for executing an authorized
operator command.

When the `ATTEMPTED` row cannot be committed:

- replay or discard is not invoked
- the operation fails closed
- the web boundary returns a safe service-unavailable response
- no persistence or exception detail is returned

### Existing command transaction behavior

The existing replay transaction design remains unchanged. Kafka
publication must not execute inside a long PostgreSQL transaction added
by this feature.

Replay continues to use its current claim, publish, and lifecycle-update
boundaries. The existing transactional discard use case also remains
unchanged.

### COMPLETED persistence failure

The delegated command may already have changed PostgreSQL state or
published to Kafka before the `COMPLETED` row is appended.

When the `COMPLETED` row cannot be committed:

- the durable `ATTEMPTED` row remains
- the command is not automatically executed again by the server
- the application raises a dedicated safe audit-persistence failure
- the web boundary returns service unavailable
- the response does not claim a successfully audited completion
- audit storage still receives no exception message or stack trace

The incomplete pair represents an operationally ambiguous completion and
must be visible through safe logs or metrics without exposing payloads,
keys, tokens, or exception details in audit persistence.

### Unexpected runtime failure

When the delegated command throws an unexpected runtime exception, the
orchestrator attempts to append:

```text
stage = COMPLETED
outcome = INTERNAL_FAILURE
errorCode = KAFKA_DEAD_LETTER_COMMAND_INTERNAL_FAILURE
```

The exception message and stack trace are not stored. After the audit
attempt, the original failure remains available only as internal
diagnostic context. The application raises a dedicated safe failure that
the web boundary maps without exposing the original exception message.

## Append-only persistence

The application output port exposes only an append operation. It does not
expose update or delete operations.

PostgreSQL reinforces append-only behavior by rejecting `UPDATE` and
`DELETE` operations on the audit table.

The schema also enforces:

- supported stage values
- supported command types
- supported outcome values
- supported safe error-code values
- stage and outcome consistency
- command type and outcome consistency
- `UNIQUE (command_id, stage)`

Indexes support investigation by operator identifier, dead-letter record
identifier, command type, and occurrence time.

## Foreign-key policy

The audit table does not use foreign keys for `operatorId` or
`deadLetterRecordId`.

A `NOT_FOUND` command result must remain auditable even though no target
record exists. Audit history must also survive future retention or
deletion policies applied to users or dead-letter records.

The stored UUID values are immutable historical identifiers, not
references that require cascading lifecycle behavior.

## Authorization boundary

Anonymous and unauthorized requests are rejected by Spring Security
before the controller and audited application use cases are invoked.
They create no operator audit rows.

Only requests that cross the authenticated and authorized operations
boundary are audited.

Audit persistence must never receive or derive values from:

- untrusted identity headers
- request email addresses
- Kafka payloads
- Kafka record keys
- access tokens
- exception messages
- stack traces
- replay lease metadata

## Consequences

### Positive

- every authorized command attempt leaves a durable initial trace
- successful, rejected, and failed commands receive explicit outcomes
- operator identity comes from a trusted token claim
- replay and discard behavior remains independently testable
- audit persistence cannot silently expand into sensitive-data storage
- PostgreSQL constraints reinforce application invariants
- audit history survives changes to mutable operational state

### Negative

- one operator command creates two database rows
- each command requires additional independent transactions
- audit database unavailability prevents new operator commands
- a completed command may have an ambiguous HTTP result when its final
  audit row cannot be committed
- database-level append-only enforcement adds migration and integration
  test complexity

## Alternatives considered

### Write one audit row after command completion

Rejected because a process or persistence failure could leave no durable
evidence that an authorized command started.

### Update one row from attempted to completed

Rejected because mutation weakens the append-only history and removes the
distinction between facts observed at different times.

### Audit directly inside the controller

Rejected because it would couple HTTP and Spring Security concerns to
persistence and duplicate orchestration behavior.

### Add audit calls inside the existing replay and discard services

Rejected because those services already have focused lifecycle
responsibilities and are useful independently of the operator HTTP
boundary.

### Wrap Kafka replay publication in one database transaction

Rejected because Kafka and PostgreSQL do not share a local transaction,
and broker publication must not hold a long PostgreSQL transaction open.

### Store the operator email address

Rejected because email is mutable personal data. The verified UUID
subject is the stable identity required by the audit use case.

### Store exception messages

Rejected because exception text may contain payload fragments,
infrastructure details, identifiers, or other sensitive information.

### Use only application-level append enforcement

Rejected as the sole guarantee because accidental SQL could still mutate
or delete historical rows.

## Verification

The decision is verified through:

- audit model invariant tests
- replay and discard outcome-mapping tests
- audited orchestration unit tests
- PostgreSQL schema and constraint tests
- PostgreSQL append-only tests
- independent transaction integration tests
- MockMvc authentication and authorization tests
- tests proving denied requests do not invoke audit collaborators
- tests proving sensitive fields are absent
- the complete Maven verification build

## Out of scope

The following capabilities are not required for Issue #53:

- public audit query endpoints
- audit export
- audit retention jobs
- command approval workflows
- command rate limiting
- payload remediation
- operator email snapshots
