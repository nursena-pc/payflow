# ADR 0008: Query Kafka dead-letter command audits

- Status: Accepted
- Date: 2026-07-25

## Context

PayFlow persists an append-only PostgreSQL audit trail for authorized Kafka
dead-letter replay and discard commands.

ADR 0007 defines the command-audit write path. Each authorized command receives
one generated `commandId` and appends:

1. an `ATTEMPTED` row before command execution
2. a `COMPLETED` row after a safe outcome is observed, when that row can be
   committed

The stored rows are durable operational evidence. They are not currently
available through an explicit application query boundary.

Operators need to investigate:

- which authorized operator issued a command
- which dead-letter record was targeted
- whether the command was replay or discard
- when the command was attempted
- whether a safe completion outcome was recorded
- which fixed safe error code applies to an unsuccessful completion

The query capability must preserve the data-minimization and append-only
guarantees established by ADR 0007.

## Decision

PayFlow will expose a dedicated, secured, read-only query boundary for Kafka
dead-letter command audits.

The query boundary will provide:

```text
GET /api/v1/operations/kafka/dead-letter-command-audits
GET /api/v1/operations/kafka/dead-letter-command-audits/{commandId}
```

Both endpoints require the existing `PAYFLOW_OPERATIONS` authority.

The list endpoint returns individual immutable audit facts. The command
endpoint returns the ordered audit timeline for one `commandId`.

Query operations do not append command-audit rows. They do not mutate audit
rows or dead-letter lifecycle state.

## Application contracts

The application layer will define dedicated query contracts:

- `KafkaDeadLetterCommandAuditFilter`
- `KafkaDeadLetterCommandAuditPage`
- `KafkaDeadLetterCommandAuditTimeline`
- `ListKafkaDeadLetterCommandAuditsQuery`
- `ListKafkaDeadLetterCommandAuditsUseCase`
- `GetKafkaDeadLetterCommandAuditTimelineUseCase`
- `KafkaDeadLetterCommandAuditQueryPort`

The existing `KafkaDeadLetterCommandAuditPort` remains append-only and
write-focused. Read operations will not be added to that port.

Separating append and query ports prevents query requirements from expanding
the command-audit write abstraction into update or delete behavior.

## Safe data boundary

The application query models may contain only fields already present in the
safe audit model:

- audit record UUID
- command UUID
- stage
- operator UUID
- dead-letter record UUID
- command type
- outcome
- safe error code
- occurrence timestamp

The HTTP adapter will use explicit response DTOs. It will not serialize JDBC
rows, security principals, exceptions, or persistence objects directly.

The query boundary must never return or derive:

- Kafka payloads
- Kafka record keys
- JWTs or access tokens
- request headers
- operator email addresses
- exception messages
- stack traces
- replay lease owners
- arbitrary persistence errors

## List filtering and pagination

The list endpoint supports optional filters for:

- `commandId`
- `operatorId`
- `deadLetterRecordId`
- `commandType`
- `stage`
- `outcome`

Pagination is zero-based.

Defaults are:

- `page=0`
- `size=20`

The maximum page size is `100`.

List ordering is deterministic:

1. `occurredAt DESC`
2. `id DESC`

The audit record UUID is the final tie-breaker because multiple independently
committed audit rows may share the same PostgreSQL timestamp precision.

An empty result is represented as a successful empty page.

## Command timeline semantics

A command timeline is identified by one non-null `commandId`.

A valid timeline contains:

```text
ATTEMPTED
```

or:

```text
ATTEMPTED
COMPLETED
```

The entries are returned in chronological command-stage order.

An `ATTEMPTED`-only timeline is valid and remains visible. It means PayFlow
durably recorded that the authorized command started but no safe completion
fact is available.

The query layer must not infer a `COMPLETED` row from:

- current dead-letter lifecycle state
- application logs
- Kafka state
- an HTTP response
- absence of a known error

All entries in one timeline must agree on:

- `commandId`
- `operatorId`
- `deadLetterRecordId`
- `commandType`

A request for an unknown command identifier returns the safe not-found
contract.

## Authorization boundary

Spring Security rejects anonymous and unauthorized requests before the web
controller invokes an application query use case.

Expected behavior is:

- missing or invalid authentication: `401 Unauthorized`
- authenticated principal without operations authority: `403 Forbidden`
- authorized operator: query use case may execute

Operator identity is not accepted as an authorization substitute. The
`operatorId` list filter selects historical rows and does not grant access.

## Persistence boundary

A dedicated JDBC adapter will implement
`KafkaDeadLetterCommandAuditQueryPort`.

The adapter may execute only `SELECT` statements against
`kafka_dead_letter_command_audits`.

The existing database trigger continues to reject `UPDATE` and `DELETE`.
No query migration may weaken:

- append-only enforcement
- stage constraints
- command type constraints
- outcome and error-code consistency
- `UNIQUE (command_id, stage)`
- historical audit data

The existing unique index beginning with `command_id` supports command
timeline lookup.

The list adapter will use the exact documented filter and ordering shapes.
Before completing the persistence checkpoint, PostgreSQL query plans will be
reviewed. An additive Flyway migration will introduce or extend query indexes
when required, including the `id` ordering tie-breaker. Existing migrations
will not be edited after release.

## Error behavior

The web boundary maps failures to the existing safe API error contract.

Expected outcomes are:

- `200 OK` for list and timeline results
- `400 Bad Request` for invalid pagination, UUID, or enum input
- `401 Unauthorized` for missing or invalid authentication
- `403 Forbidden` for authenticated non-operators
- `404 Not Found` when no rows exist for a command identifier
- `500 Internal Server Error` for an unexpected safe internal failure

SQL text, database details, exception messages, and stack traces are not
returned.

## Consequences

### Positive

- operators can investigate durable command history through an explicit API
- incomplete command histories remain visible instead of being fabricated
- application and HTTP responses retain a strict safe-field allowlist
- query functionality does not weaken the append-only write boundary
- pagination and ordering are stable across repeated requests
- persistence tuning can evolve through additive migrations

### Negative

- the feature adds application, JDBC, HTTP, OpenAPI, Postman, and test surface
- flexible filters require careful SQL construction and query-plan review
- deterministic pagination may require additional PostgreSQL indexes
- operator UUIDs remain sensitive operational identifiers and require strict
  authorization

## Alternatives considered

### Add read methods to the append port

Rejected because the append port has one narrow responsibility and must not
grow toward mutation-oriented repository behavior.

### Return one mutable command projection

Rejected because it would hide the distinction between independently observed
`ATTEMPTED` and `COMPLETED` facts and could conceal incomplete histories.

### Infer completion from dead-letter lifecycle state

Rejected because lifecycle state is mutable and is not authoritative evidence
that a specific authorized command completed.

### Return only completed commands

Rejected because an `ATTEMPTED`-only history is operationally important and
must remain queryable.

### Expose application logs instead of a query API

Rejected because logs are not the authoritative durable audit store and may
have different availability and retention guarantees.

### Allow arbitrary sorting

Rejected for the initial capability because it expands SQL and index
complexity without a demonstrated operational requirement.

### Include operator email addresses

Rejected because email is mutable personal data and is not stored in the
audit trail.

## Verification

The decision will be verified through:

- query-model invariant tests
- pagination and filter tests
- complete and incomplete timeline tests
- application service tests
- JDBC mapping and PostgreSQL integration tests
- query-plan and migration tests when indexes are added
- MockMvc authentication and authorization tests
- tests proving query requests do not append audit rows
- tests proving sensitive fields are absent
- OpenAPI JSON contract tests
- Postman structure checks
- complete Maven clean verification

## Out of scope

- audit record updates or deletion
- audit retention jobs
- CSV or bulk export
- public or user-facing audit access
- command approval workflows
- operator email enrichment
- Kafka payload or record-key inspection
- exception-message exposure
- analytics dashboards
- changes to replay or discard command behavior
