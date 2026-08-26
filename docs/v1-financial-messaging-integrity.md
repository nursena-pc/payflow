# v1.0.0 Financial and Messaging Integrity Contract

Status: Active v1.0.0 release-candidate integrity contract

Tracking issue: #194

Baseline: `315488e88f2b69c56d77fc64b49dfc1c6497649f`

Project version: `1.0.0-SNAPSHOT`

## Purpose

This document records the current integrated financial and messaging integrity
boundary used by the PayFlow v1.0.0 release candidate.

Checkpoint 3 is release verification and evidence closure. It does not add a
new transfer capability, messaging topology, persistence model, or payment
provider.

The initial CP3 diagnostic discovered 119 test classes across the transaction,
wallet, ledger, outbox, and event-processing packages. Those classes executed
564 focused tests with zero failures, errors, or skips from the exact baseline,
and the repository remained unchanged.

No runtime integrity defect was identified by that diagnostic. The CP3
candidate therefore closes current-state documentation and executable-contract
drift without changing runtime financial or messaging behavior.

## Financial transaction boundary

Wallet-to-wallet transfer remains one Spring-managed PostgreSQL unit of work
for the financial state that must commit atomically.

The implemented transfer path preserves:

- authenticated source-wallet ownership derived from the verified JWT subject;
- positive, precision-bounded monetary amounts;
- active-wallet and matching-currency validation;
- source-wallet debit and target-wallet credit;
- payment-transaction persistence and completion;
- one immutable debit and one immutable credit ledger entry;
- transactional-outbox publication intent.

An exception that escapes the required unit of work rolls back the state that
must remain atomic. Partial wallet, payment-transaction, ledger, or outbox
success is not an accepted transfer outcome.

Automatic retry is not applied to financial mutations. A caller must use the
existing idempotency contract rather than depending on hidden financial
re-execution.

## Idempotency and concurrency

Every transfer requires the existing normalized `Idempotency-Key`.

PostgreSQL remains the final authority for ownership of the
`source_wallet_id + idempotency_key` pair. Application lookup provides the
normal replay/conflict behavior, while the database uniqueness boundary closes
the concurrent race in which multiple callers initially observe no row.

A completed matching replay returns the existing transfer result and must not
create an additional financial movement.

A conflicting payload remains an idempotency conflict. An unfinished request
remains an in-progress conflict.

Concurrent or retried requests must not create more than one authoritative
payment transaction, one source debit, one target credit, one debit ledger
entry, one credit ledger entry, or one completed-transfer outbox intent for the
same accepted transfer identity.

## Wallet and ledger durability

PostgreSQL remains the durable system of record for implemented financial
state.

Wallet balances represent current state. Immutable double-entry ledger entries
provide the durable accounting explanation for completed movements.

The ledger contract requires one debit and one credit with equal amount and
currency, the same payment transaction, and different wallets. PostgreSQL
constraints complement domain invariants and transaction rollback.

Redis is not a financial source of truth. Its current use remains bounded to
explicitly expiring abuse-control state.

## Transactional outbox

The completed transfer and its integration-event intent are persisted in the
same PostgreSQL transaction.

The transfer use case does not directly publish to Kafka inside the financial
database transaction.

After commit, the outbox publisher claims eligible records through the existing
leased lifecycle and publishes them asynchronously. Publication success,
failure, retry timing, attempt accounting, lease recovery, and terminal failure
remain durable PostgreSQL state.

A Kafka outage does not fabricate publication success and does not invalidate a
financial transfer that already committed with its durable outbox intent.

## Kafka delivery and processing

Kafka delivery remains at-least-once.

Exactly-once delivery is not claimed.

A producer acknowledgement timeout is ambiguous: the application may not know
whether Kafka ultimately accepted the send. A retry may therefore cause a
duplicate broker delivery.

The durable duplicate-processing boundary is PostgreSQL-backed consumer
idempotency using the existing logical consumer/event identity. Duplicate
delivery must not create a second effective processing result or a second
financial movement.

Global ordering across unrelated transfers is not promised.

## Dead-letter intake and controlled recovery

Permanent or exhausted transfer-completed failures may enter the configured
dead-letter path.

Dead-letter intake remains durable in PostgreSQL and idempotent by physical DLT
location. Replay is explicit rather than automatic.

Replay retains the current bounded lease, replay-lineage, replay-attempt, and
chain-wide attempt-limit rules. Replay publication remains at-least-once
because PostgreSQL and Kafka do not share one local transaction.

Discard remains an explicit terminal operator decision for eligible records.

Operators must not manually edit outbox or dead-letter rows to manufacture
success, and a client-side Kafka timeout must not be interpreted as proof that
the broker rejected the record.

## Operations authorization and audit

Current dead-letter command operations are exposed only below the existing
`/api/v1/operations/**` authorization boundary.

`PAYFLOW_OPERATIONS` is required for those routes.

The current implementation includes authorized replay and discard endpoints,
dead-letter query endpoints, append-only command auditing, command-audit list
queries, and command-audit timeline queries.

Replay and discard command auditing preserves the fail-closed requirement for
the initial `ATTEMPTED` audit fact. The append-only audit model stores bounded,
safe operational facts rather than Kafka payloads, access tokens, exception
stacks, or operator email addresses.

Command-audit query operations are read-only and do not mutate dead-letter
lifecycle state or append new command-audit facts.

## Dependency failure and recovery boundary

The current Redis/Kafka outage rehearsal remains relevant evidence for this
checkpoint where it intersects integrity guarantees.

PostgreSQL remains the durable system of record. Redis state remains ephemeral
and non-financial.

During Kafka unavailability, durable outbox and dead-letter lifecycle state must
continue to represent unresolved work rather than fabricated success. After the
dependency recovers, the existing lifecycle resumes without manual status
surgery.

The documented acknowledgement-ambiguity limitation remains part of the
contract. This checkpoint does not convert local Testcontainers recovery
evidence into high-availability, zero-data-loss, RPO/RTO, or production
certification.

## Historical ADR boundary

ADR 0002 and ADR 0003 describe the implemented double-entry and PostgreSQL
idempotency decisions.

ADR 0004 records the transactional-outbox decision and its at-least-once
delivery boundary.

ADR 0005 records the original controlled-replay foundation. Its historical body
contains statements from the stage before replay HTTP exposure existed.
Subsequent ADRs and implementation added the protected operations boundary,
command auditing, and query capabilities. The ADR body remains historical
decision evidence rather than being rewritten as if authored after those later
increments.

ADR 0008 similarly records the command-audit query design in future-tense
language from the implementation increment in which it was introduced. The
current endpoints and query implementation now exist; the ADR body remains the
historical design record.

## Verification boundary

The initial focused CP3 diagnostic passed:

- 119 discovered test classes;
- 564 executed tests;
- zero failures;
- zero errors;
- zero skipped tests;
- no repository mutation.

The final CP3 candidate must still pass complete Maven verification. Its exact
unchanged pull-request head must then pass GitHub CI and Docker Smoke before
expected-head merge.

A green diagnostic is evidence to preserve current behavior. It is not a reason
to refactor financial or messaging runtime code during release hardening.

## CP3 does not add or activate

CP3 does not add or activate:

- new wallet, transfer, payment, or public API features;
- real payment-provider integration;
- microservice extraction or distributed-transaction redesign;
- Kafka topology redesign or broker replacement;
- exactly-once delivery guarantees;
- automatic retry for financial mutations;
- database schema or migration changes without a verified blocker;
- new Redis financial durability responsibilities;
- unrelated dependency, workflow, or deployment changes;
- regulatory, production-certification, or real-money claims.
