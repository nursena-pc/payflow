# Redis and Kafka Outage/Recovery Rehearsal

Issue: [#178](https://github.com/nursena-pc/payflow/issues/178)

PayFlow v0.16.0 uses this stabilization rehearsal to verify the already-reviewed
Redis and Kafka dependency-failure contracts without changing runtime API,
security, retry, or persistence semantics.

The committed entry point is:

```powershell
.\scripts\operations\redis-kafka-outage-recovery-rehearsal.ps1
```

The procedure is local Testcontainers evidence. It is not production
high-availability, disaster-recovery, RPO/RTO, or zero-data-loss
certification.

## Boundaries under test

PostgreSQL remains the durable system of record. Redis contains only bounded,
expiring abuse-control state. Kafka is asynchronous delivery and
event-processing infrastructure.

The rehearsal does not activate generalized registration abuse protection.
Registration remains the reviewed `DEFER` case. Login-limiter thresholds,
generalized abuse limits, Kafka producer/consumer retries, DLT behavior, and
replay semantics are not retuned to make the rehearsal pass.

Two focused executable contracts own the runtime proof:

- `V016RedisOutageRecoveryRehearsalTest`
- `V016KafkaOutageRecoveryRehearsalTest`

Both use isolated PostgreSQL and dependency containers created by Testcontainers.
Dependency isolation targets the exact container ID created by the test through
Docker pause/unpause. It never selects or stops a developer service by a generic
container name.

## Prerequisites

Before running:

- Docker must be available.
- `JAVA_HOME` must point to Java 21.
- the Maven wrapper must be present.
- HEAD must be attached to a branch.
- the Git working tree must be clean.
- `.runtime/` must remain ignored by Git.

The script refuses a dirty tree and never falls back to an unrelated bare Java
runtime. It does not run `git reset --hard`, `git clean`, `docker compose down`,
or volume deletion.

## Redis healthy, outage, and recovery proof

The Redis contract starts PostgreSQL 17 and Redis 8 in isolated Testcontainers
targets. The existing login limiter remains enabled. Generalized abuse
protection is enabled only for the already-implemented password-recovery
request workflow, while registration protection is explicitly disabled.

Healthy-state proof requires:

- the mapped Redis endpoint answers a raw Redis `PING`;
- an invalid login reaches the normal coarse `401` path;
- an eligible synthetic password-recovery request returns the existing generic
  `202 Accepted`;
- exactly one password-recovery credential and one mail-outbox row are created
  for that eligible healthy request.

The rehearsal then pauses only the exact Redis test container.

While Redis is unavailable:

- login must fail closed with HTTP `503`;
- the public code remains `LOGIN_RATE_LIMIT_UNAVAILABLE`;
- the response must not expose Redis host, mapped port, connection class, or
  connection-refused details;
- password recovery must preserve the anti-enumeration `202 Accepted` response;
- the fail-closed password-recovery path must create no credential and no mail
  side effect;
- the reviewed login-limiter and generalized abuse Redis-failure counters must
  increase; and
- the synthetic PostgreSQL user fingerprint must remain unchanged.

After the same container is unpaused, the same mapped Redis endpoint must
recover and the running application client must resume the normal protected
request path without key surgery, PostgreSQL repair, application restart, or
semantic retuning.

Redis state is intentionally ephemeral. Recovery of Redis availability is not
a claim that previously held quota counters survive a real process replacement
or data-loss event. PostgreSQL business/session state is a separate durable
boundary.

## Kafka healthy outbox proof

The Kafka contract starts isolated PostgreSQL 17 and Kafka infrastructure with
the existing transfer-completed consumer and DLT intake enabled only for the
test.

A deterministic synthetic outbox event is inserted using the current schema.
Healthy publication must:

- claim exactly one available event;
- mark that outbox event `PUBLISHED`;
- publish the semantic JSON payload to Kafka;
- reach exactly one processed-event idempotency row; and
- reach exactly one transfer-completed audit row.

JSON payloads are compared semantically rather than as raw strings because the
PostgreSQL `jsonb` representation may normalize whitespace and object-key order.

## Kafka broker outage and durable outbox recovery

A second synthetic outbox event is stored in PostgreSQL before the exact Kafka
test container is paused.

While Kafka is unavailable, the existing publisher must not fabricate success.
The event remains durably represented through the existing retry lifecycle:
`PENDING`, incremented attempt count, future `available_at`, and bounded
`last_error` state with no lingering lease.

After Kafka is unpaused and the existing retry delay becomes eligible, the same
durable event must publish successfully and transition to `PUBLISHED`.

The rehearsal does not manually update outbox status to force recovery.

## DLT intake and replay recovery

The test sends one deterministic invalid transfer-completed record through the
existing consumer failure path. Bounded retries and the configured DLT must
lead to a durable PostgreSQL `kafka_dead_letter_records` row.

A separate deterministic replayable dead-letter row then exercises replay while
Kafka is unavailable. The existing replay service must persist
`REPLAY_FAILED`, clear the lease, increment replay accounting, and retain a
bounded error rather than claiming replay success.

After Kafka recovery, a second replay claim must complete as `REPLAYED` and
preserve the replay-origin and replay-attempt headers.

## Kafka acknowledgement ambiguity

A producer send that times out at the application acknowledgement boundary is
not proof that the broker did not eventually accept that send.

The pre-commit runtime probe for #178 demonstrated this boundary directly: a
timed-out replay attempt could become visible after the broker recovered even
though the durable replay record had already moved to `REPLAY_FAILED`.

Therefore the committed rehearsal deliberately tolerates a delayed earlier
replay attempt while requiring the later successful attempt to appear with the
correct attempt header.

This is an at-least-once delivery boundary, not a zero-duplicate delivery
claim. Safety is established at the durable PostgreSQL processing boundary:
even if both replay deliveries become visible, `processed_kafka_events` and the
transfer-completed audit remain single-record for the event. The rehearsal also
proves that no `payment_transactions` or `ledger_entries` rows are added by
these synthetic delivery/replay checks.

Operators must not manually mark an outbox or dead-letter record successful
solely because a client-side Kafka timeout occurred.

## Observable symptoms and safe operator actions

For a Redis incident, expected signals include coarse login `503` responses and
the existing bounded Redis-failure metrics. Account-action workflows with the
reviewed fail-closed anti-enumeration contract may continue returning generic
accepted responses while suppressing protected side effects.

Safe recovery is to restore the configured Redis dependency, verify the
dependency endpoint is reachable, and verify protected application requests
resume. Do not disable the login limiter, change dependency failure mode, or
activate registration protection as an incident shortcut.

For a Kafka incident, expected signals include outbox retry/error state, Kafka
producer errors, consumer retry/DLT activity, and durable replay status. Safe
recovery is to restore broker availability, verify pending outbox work becomes
eligible and publishes through the existing lifecycle, inspect durable DLT
records, and use the existing authorized replay path where appropriate.

Do not edit PostgreSQL outbox/DLT rows manually to manufacture success. Do not
assume a timed-out send was definitely lost. Escalate when the dependency is
healthy but the application does not recover, durable state no longer advances
according to the documented lifecycle, replay remains unresolved, or invariant
counts diverge.

## Evidence and privacy

The committed script writes only sanitized bounded evidence below:

```text
.runtime/dependency-outage-recovery/<timestamp>/evidence.txt
```

`.runtime/` is ignored by Git.

Evidence contains branch/HEAD, toolchain version, PASS/FAIL counts, named
bounded invariants, and limitations. It must not contain real email addresses,
user identifiers, raw client addresses, passwords, JWTs, refresh credentials,
MFA material, Redis keys, real mail content, real financial payloads, or
production data.

All test identities use fixed documentation-only `example.invalid` values and
synthetic UUIDs.

## Reviewed pre-commit proof

Before committing the permanent rehearsal contract, issue #178 was exercised on
exact baseline:

```text
ed9cf4259bf6dd24185fa4bf258bec8593d6f1b8
```

The Redis reversible-isolation probe passed one test with zero failures, errors,
or skips and proved same-endpoint recovery plus automatic application-client
recovery.

The Kafka reversible-isolation probe passed one test with zero failures, errors,
or skips and proved semantic JSON publication, durable outbox retry/recovery,
durable DLT intake, replay failure/recovery, replay header accounting,
acknowledgement ambiguity handling, and a single durable idempotency/audit
boundary with unchanged payment and ledger row counts.

Earlier exploratory failures were retained as issue evidence and classified
before source changes: raw JSON string equality was a probe defect; Redis
stop/start failed below the application at the mapped-host-port layer; and the
first replay header assertion exposed acknowledgement ambiguity rather than a
PayFlow lifecycle defect.

## Limitations

This rehearsal does not certify:

- Redis persistence, replication, Sentinel, or cluster failover;
- Kafka multi-broker failover, partition reassignment, or capacity;
- exactly-once end-to-end delivery;
- zero data loss;
- production retention or recovery objectives;
- production RPO/RTO;
- point-in-time recovery;
- production disaster recovery; or
- real-money processing.

Those claims require separate infrastructure, evidence, and review.
