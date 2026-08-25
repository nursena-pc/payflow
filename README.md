# PayFlow

PayFlow is a simulated digital wallet and payment transaction backend built to demonstrate production-oriented Java backend engineering. It does **not** process real money.

## Goals

The project focuses on the backend problems that distinguish a financial system from a CRUD demo:

- transactional consistency
- idempotent transfer processing
- concurrent wallet updates
- double-entry ledger records
- authentication and authorization
- event-driven processing
- observable and testable infrastructure

## Technology stack

- Java 21
- Spring Boot 3.5
- Spring MVC, Spring Data JPA, Hibernate, Spring Security, Spring Mail
- PostgreSQL and Flyway
- Redis
- Apache Kafka
- Maven
- JUnit 5, Mockito, Testcontainers
- OpenAPI / Swagger UI
- Docker and Docker Compose
- GitHub Actions
- Spring Boot Actuator and Prometheus

## Architecture

PayFlow starts as a **modular monolith**. Business modules use inward-facing dependencies:

```text
HTTP / Persistence / Messaging adapters
                 â†“
         Application use cases
                 â†“
             Domain model
```

This keeps domain rules independent from Spring and infrastructure while avoiding premature microservices. See [Architecture](docs/architecture.md) and the [ADR records](docs/adr).

## Current status

The repository foundation, identity flow, revocable refresh sessions, Redis-backed login protection, wallet management, transactional transfer processing, Kafka delivery pipeline, and secure dead-letter operations control plane are implemented.

Completed capabilities include:

- repository standards, contribution guidelines, and CI verification
- Docker-based PostgreSQL, Redis, Kafka, and Mailpit infrastructure
- versioned PostgreSQL schema management with Flyway
- secure-by-default Spring Security configuration
- user registration with normalized email addresses
- digest-only email-verification credentials issued after registration
- generic email-verification request and single-use confirmation endpoints
- generic password-recovery request and atomic password/session reset endpoints
- AES-256-GCM-protected account-action mail outbox with leased SMTP delivery
- login eligibility gated by verified email only after password validation
- BCrypt password hashing
- user login with RSA-signed JWT access tokens and opaque refresh credentials
- stable JWT `kid` issuance with RS256 algorithm pinning
- active and previous RSA verification-key overlap for controlled rotation
- fail-fast production loading of PKCS#8 private and X.509 public keys
- Redis-backed fixed-window login limits by normalized identity and spoofing-resistant effective client address
- explicit trusted reverse-proxy CIDR validation with literal-only IPv4 and IPv6 parsing
- deterministic `Forwarded` / `X-Forwarded-For` resolution with safe direct-peer fallback
- bounded client-context decision metrics without raw address labels or logs
- atomic Lua counter updates with explicit TTL, `429 Retry-After`, and fail-closed `503`
- low-cardinality login-protection metrics and credential-free security events
- refresh-session architecture and threat model with explicit rotation and reuse-detection boundaries
- refresh-token family and record domain/application contracts
- PostgreSQL refresh-session persistence with SHA-256 digest-only token storage
- constrained refresh-token lineage, expiration, consumption, and family revocation semantics
- pessimistic row locking for concurrent refresh-session mutation
- clean-install and V13-to-V14 refresh-session migration coverage
- authenticated current-user profile
- authenticated wallet creation and current-wallet retrieval
- one-wallet-per-user enforcement at application and database levels
- authenticated simulated wallet top-up
- aggregate-based wallet balance mutation
- PostgreSQL optimistic locking for concurrent wallet updates
- authenticated wallet-to-wallet transfer
- source-wallet identity derived from the authenticated JWT subject
- transfer lifecycle persistence with `PENDING` and `COMPLETED` states
- atomic source-wallet debit and target-wallet credit
- immutable double-entry ledger records
- source-wallet-scoped `Idempotency-Key` uniqueness
- completed-transfer replay for repeated requests with the same payload
- stable conflict responses for key reuse with a different payload
- complete rollback when ledger persistence fails
- controlled concurrent duplicate-transfer verification
- real PostgreSQL integration tests with Testcontainers
- real JWT endpoint-to-database transfer verification
- authenticated current-wallet transaction history
- incoming and outgoing transaction direction derivation
- filtering by direction, transaction status, and date range
- inclusive `from` and exclusive `to` date boundaries
- deterministic pagination using `createdAt DESC, id DESC`
- stable validation, authentication, and wallet-not-found responses
- public transaction-history responses that exclude idempotency keys
- real PostgreSQL verification of filtering, ordering, and pagination
- transactional outbox persistence in the transfer database transaction
- leased and retryable outbox publication to Kafka
- idempotent transfer-completed event processing
- durable Kafka dead-letter intake with replay lineage
- controlled replay and explicit discard lifecycle operations
- operator-only dead-letter query and command endpoints
- append-only PostgreSQL audit records for authorized replay and discard commands
- operator-only, paginated command-audit queries and chronological command timelines
- Prometheus metrics, Grafana dashboards, and alert rules for Kafka consumer failures

OpenAPI documentation covers the implemented API. Across the committed standard, MFA, login-rate-limit, and compatibility-coverage collections, Postman represents all 30 canonical `/api/v1` operations while keeping lifecycle-sensitive and privileged requests manually gated. PayFlow v0.16.0 is the latest published release, while the active release-candidate development line uses Maven version `1.0.0-SNAPSHOT`. The v1.0.0 stage is tracked by umbrella issue #189 and development-start issue #190 and is limited to release hardening and evidence closure unless a separately reviewed defect requires change. Registration remains evidence-backed `DEFER`, and the existing password-login limiter semantics remain unchanged.

The immutable v0.16.0 publication record is anchored to annotated tag `v0.16.0` with tag object `8308e190960525924a550dafc8dcfcf61d4250d0`, merge commit `da8cefa9772d8e009b5ef1e5ab53d03bc44b1c13`, and successful Release workflow run [32757038003](https://github.com/nursena-pc/payflow/actions/runs/32757038003). GitHub Release ID `375880233` was published at `2026-08-24T17:40:22Z`. The published `payflow-0.16.0.jar` is 100566879 bytes and its independently verified SHA-256 is `8c542fc6928179345e5cda3d0f66d1481f7277a88096a52a69952ed95f2958e6`; checksum asset SHA-256 `b14f5ea137012e7aa8557fa21c1c9fece151deb2447a0b636da7ee3a173d14b0` names and matches that JAR, and the published release notes exactly match the reviewed versioned notes.

The immutable v0.15.0 publication record remains anchored to annotated tag `v0.15.0`, merge commit `c29a067ca3a64514444e17db59a2b862d26f5950`, and successful release workflow run [32172653513](https://github.com/nursena-pc/payflow/actions/runs/32172653513). The published `payflow-0.15.0.jar` is 100236578 bytes and its independently verified SHA-256 is `7EDF5EAD1EB93966E750F917D9472B4383D2B3CDA7406A264AE78B106A779080`.

The immutable v0.14.0 publication record remains anchored to annotated tag `v0.14.0`, merge commit `d65929b98bb66b22f208d26f75a764e1ade78b6a`, and successful release workflow run [31728977714](https://github.com/nursena-pc/payflow/actions/runs/31728977714). The published `payflow-0.14.0.jar` is 100200050 bytes and its independently verified SHA-256 is `A6533039C5DDBE610D9DDB986DDBDAFE192DD56BE664E86B65A72AECF51F116E`.

The immutable v0.13.0 publication record remains anchored to annotated tag `v0.13.0`, merge commit `726f631a0de800870813ccb0c00b2676eb5d172b`, and successful release workflow run [31115952987](https://github.com/nursena-pc/payflow/actions/runs/31115952987). The published `payflow-0.13.0.jar` is 100015861 bytes and its independently verified SHA-256 is `78520B04BA3FDAF1BCEB3EAF29FCBE96C46265DF691C52C9048CEE6B5D58F4DA`.

See the [v0.16.0 release notes](docs/releases/v0.16.0.md), the [published v0.16.0 GitHub Release](https://github.com/nursena-pc/payflow/releases/tag/v0.16.0), the [v0.15.0 release notes](docs/releases/v0.15.0.md), the [abuse-protection operations guide](docs/operations/abuse-protection-observability.md), [ADR 0015](docs/adr/0015-generalized-abuse-protection.md), and the [roadmap](docs/roadmap.md).

## PostgreSQL backup/restore rehearsal

v0.16.0 Increment 2 is tracked by [Issue #173](https://github.com/nursena-pc/payflow/issues/173). The committed local procedure creates a PostgreSQL 17 custom-format backup, restores only into a clean isolated target, verifies Flyway and persistence fingerprints, and starts PayFlow against the restored database without changing runtime API or security behavior.

See the [PostgreSQL backup/restore operations guide](docs/operations/postgresql-backup-restore.md) for prerequisites, source-selection safeguards, evidence boundaries, exact commands, and recovery limitations.

## Flyway clean-install / upgrade rehearsal

v0.16.0 Increment 3 is tracked by [Issue #175](https://github.com/nursena-pc/payflow/issues/175). The committed rehearsal proves a fresh PostgreSQL 17 database reaches the complete V1 through V24 schema and separately proves the immutable v0.13.0 / V17 release baseline upgrades through V18 through V24 while preserving deterministic synthetic representative data.

The rehearsal rejects historical V1 through V17 migration drift, verifies the complete current Flyway history and database invariants, starts PayFlow against both resulting databases, and keeps recovery responsibility in the separate backup/restore boundary.

See the [Flyway clean-install / upgrade operations guide](docs/operations/flyway-clean-upgrade.md) for the approved previous-release baseline, synthetic-data fingerprint contract, exact command, failure behavior, and rollback limitations.

## Redis/Kafka outage-recovery rehearsal

v0.16.0 Increment 4 is tracked by [Issue #178](https://github.com/nursena-pc/payflow/issues/178). The committed rehearsal uses isolated Testcontainers targets to verify Redis fail-closed behavior and recovery, PostgreSQL-backed Kafka outbox durability, consumer/DLT persistence, replay recovery, and the documented at-least-once acknowledgement-ambiguity boundary without retuning runtime semantics.

Registration abuse protection remains deferred, the separate login limiter keeps its existing contract, and PostgreSQL remains the durable system of record. The rehearsal does not claim Redis/Kafka high availability, exactly-once end-to-end delivery, zero data loss, production RPO/RTO, or real-money operation.

See the [Redis/Kafka outage-recovery operations guide](docs/operations/redis-kafka-outage-recovery.md) for the exact command, observable symptoms, safe operator actions, recovery checks, idempotency boundary, privacy requirements, and limitations.

## Structured logging

PayFlow supports single-line JSON logs through the `structured-logging` and `production` Spring profiles. HTTP requests emit one bounded completion event containing the correlation ID, route template, method, status, duration, and outcome without logging bodies, query strings, authorization headers, cookies, raw URI paths, or financial/user data.

See the [structured logging operations guide](docs/operations/structured-logging.md) for activation, field contracts, redaction boundaries, and verification commands.

## v0.11.0 release

PayFlow v0.11.0 was published from merge commit `00401d55546fb819fe7d96a8fad8e8c43e37649c`. The release freezes trustworthy request correlation, structured JSON logging, bounded request-completion events, centralized redaction, OpenAPI/Postman contracts, operations guidance, and Docker smoke verification.

The tag-triggered release workflow rebuilt and verified the project before publishing `payflow-0.11.0.jar`, its SHA-256 checksum, and the GitHub Release.

See the [v0.11.0 release notes](docs/releases/v0.11.0.md) and the [structured logging operations guide](docs/operations/structured-logging.md).

## v0.12.0 release

PayFlow v0.12.0 was published from merge commit `fb0f97d076864cf3e45aabe0e3c25c81520ee101`. The release separates JWT key retrieval from token issuance and verification. New tokens carry the configured active `kid`. The resource server accepts only RS256 tokens whose key identifier selects the active or immediately previous public key.

Local development uses one process-local ephemeral RSA key by default. The `production` profile requires configured PKCS#8 private and X.509 public key resources and fails startup when locations, identifiers, key strength, or the active key pair are invalid. Private keys and runtime key directories must never be committed.

The protected release-preparation PR #114 produced the verified `v0.12.0` tag target. Release workflow run `30921514114` rebuilt and verified the project before publishing the executable JAR, SHA-256 checksum, and GitHub Release.

See the [v0.12.0 release notes](docs/releases/v0.12.0.md) and the [JWT key-rotation operations guide](docs/operations/jwt-key-rotation.md). The provider decision is recorded in [ADR 0012](docs/adr/0012-jwt-signing-key-rotation.md).

## v0.13.0 release

PayFlow v0.13.0 was published from merge commit `726f631a0de800870813ccb0c00b2676eb5d172b`. The release freezes email-ownership verification, password recovery, and secure account-action mail delivery. Account-action credentials use 256 bits of cryptographically secure randomness, canonical unpadded Base64 URL encoding, purpose-specific lifetimes, digest-only persistence, serialized supersession, and pessimistically locked single-use consumption.

Registration issues the initial verification credential in the user transaction. Generic verification and recovery request endpoints do not disclose account existence or eligibility. Verification marks ownership exactly once. Password recovery reuses the registration password policy, replaces the BCrypt hash, consumes the recovery credential, and revokes every active refresh-token family atomically with the `PASSWORD_RECOVERY` reason.

Provider-ready verification and recovery links are protected with AES-256-GCM before persistence in the dedicated V17 mail outbox. SMTP delivery runs only after commit through a leased PostgreSQL dispatcher using `FOR UPDATE SKIP LOCKED`, bounded retry, credential-expiry cutoffs, and stable `Message-ID` values. Terminal delivery outcomes erase protected content, while logs and metrics exclude recipients, links, credentials, digests, and encrypted bytes. Production requires configured RSA signing material and a configured 32-byte mail-content protection key.

Feature PRs [#121](https://github.com/nursena-pc/payflow/pull/121), [#123](https://github.com/nursena-pc/payflow/pull/123), and [#125](https://github.com/nursena-pc/payflow/pull/125), plus release-preparation PR [#127](https://github.com/nursena-pc/payflow/pull/127), passed protected `build-and-test` and `docker-smoke` checks. Release workflow run [31115952987](https://github.com/nursena-pc/payflow/actions/runs/31115952987) rebuilt the tagged commit and published `payflow-0.13.0.jar` with its independently verified SHA-256 checksum.

Per-identity and per-client Redis quotas for account-action requests remain explicitly deferred to the later generalized abuse-protection milestone. v0.13.0 retains generic accepted responses and credential-safe observable output, but does not claim that deferred limiter capability.

See the [v0.13.0 release notes](docs/releases/v0.13.0.md), [ADR 0013](docs/adr/0013-secure-mail-outbox-and-smtp-delivery.md), and the [mail-delivery operations guide](docs/operations/account-action-mail-delivery.md).

## v0.14.0 release

PayFlow v0.14.0 delivers TOTP-based multi-factor authentication and purpose-bound step-up authentication as a package-bounded identity-security capability. Authenticator state, cryptographic policy, application use cases, persistence, and HTTP adapters remain separated by the existing modular-monolith boundaries.

Authenticated enrollment creates a pending 160-bit TOTP secret, protects it with AES-256-GCM before PostgreSQL V18 persistence, and activates it only after a valid proof. The plaintext secret and `otpauth://` provisioning value cross only the response that created them. Production requires dedicated MFA encryption material independent from JWT and mail keys.

Enabled MFA users complete password verification before receiving a short-lived, digest-only login challenge. PostgreSQL V19 stores bounded challenge state without plaintext credentials. A valid six-digit TOTP or one unused recovery code consumes the challenge exactly once before access and refresh credentials are issued.

Successful enrollment creates ten independent 128-bit canonical Base64URL recovery codes and returns them once. PostgreSQL V20 stores only SHA-256 digests. Recovery-code use, challenge consumption, and credential issuance share one transaction and preserve single-winner behavior under concurrency.

Authenticated users can obtain a short-lived, subject-bound, purpose-bound step-up grant by proving possession of their enabled second factor. PostgreSQL V21 stores only grant digests. The application-facing `StepUpAuthorizationPolicy` owns subject, purpose, expiry, supersession, and single-use authorization without servlet or controller coupling. Wrong-subject, wrong-purpose, expired, superseded, malformed, unknown, and replayed grants share stable coarse failure contracts.

MFA disable and recovery-code rotation consume exact step-up purposes. MFA disable removes authenticator and recovery-code state, revokes active refresh-token families, and appends credential-free audit evidence atomically. Recovery-code rotation atomically replaces the complete digest set and returns replacement plaintext once. Active-authenticator replacement remains deferred until a safe two-stage replacement lifecycle is designed and verified.

See the [v0.14.0 release notes](docs/releases/v0.14.0.md), [ADR 0014](docs/adr/0014-mfa-and-step-up-authentication.md), the [MFA threat model](docs/security/mfa-threat-model.md), the [MFA operations guide](docs/operations/mfa-security.md), the [TOTP enrollment contract](docs/security/mfa-enrollment.md), the [MFA login challenge contract](docs/security/mfa-login-challenge.md), the [recovery-code contract](docs/security/mfa-recovery-codes.md), and the [step-up contract](docs/security/step-up-authentication.md).
## v0.15.0 release

PayFlow v0.15.0 delivers the complete generalized abuse-protection implementation
and accepted performance evidence while preserving the simulated-money boundary.
The completed milestone is tracked by [issue #149](https://github.com/nursena-pc/payflow/issues/149),
with release finalization recorded by [issue #166](https://github.com/nursena-pc/payflow/issues/166).

The release covers generalized abuse protection, reproducible load and
performance evidence, and operational dashboards and alerts.

The historical foundation remains explicit: Increment 1 freezes five bounded workflow identifiers, the global generalized policy switch defaults off, and
the approved foundation is recorded by issue #151. Later increments delivered
Redis enforcement, workflow wiring, observability, and reviewed evidence
without changing that original policy boundary.

Generalized protection is wired for email-verification requests,
password-recovery requests, MFA login-challenge confirmation, and step-up grant
issuance. The application-facing policy remains independent from controllers
and servlet APIs, reuses the trusted effective-client-address boundary, and
enforces bounded per-identity and per-client decisions through one atomic,
expiring Redis operation. `ABUSE_PROTECTION_ENABLED` remains `false` by default
so deployment activation is explicit. The existing password-login limiter is a
separate compatibility contract and is unchanged.

Email-verification and password-recovery requests preserve the same empty `202
Accepted` response for eligible, ineligible, quota-limited, and fail-closed
dependency outcomes. Blocked work creates no account-action credential or mail
side effect. MFA challenge quota rejection preserves the coarse unauthorized
contract without mutating challenge attempts, recovery codes, or credentials.
Step-up rejection runs before user/authenticator locking, second-factor
consumption, or grant creation. Fail-closed Redis dependency failure for MFA and
step-up uses the existing coarse `MFA_SECURITY_UNAVAILABLE` boundary.

Increment 5 adds bounded Micrometer decision and Redis-failure metrics, the
dedicated Grafana abuse-protection dashboard, three actionable Prometheus alert
paths, and an operations runbook. Observable dimensions remain limited to
application-owned workflow, outcome, reason, and failure-mode values; email
addresses, user identifiers, raw client addresses, credentials, Redis keys,
counters, and TTL values remain prohibited.

Increment 6 adds the pinned external load harness, frozen latency/throughput/
saturation/overload budgets, quota-pressure evidence with zero bypass, and
reviewed developer-workstation performance evidence under
`docs/performance/evidence/`. This evidence is not a production capacity
certification.

The bounded registration experiment produced an evidence-backed `DEFER`
decision. No material resource-exhaustion risk was demonstrated through the
tested 16 registrations/second ceiling, so generalized registration protection
is not wired in v0.15.0. The existing registration `201` / `400` / `409` public
contract remains unchanged. This does not claim absence of risk above the tested
range; a future `ACTIVATE` decision requires new evidence and a separately
reviewed implementation/comparison checkpoint.

See the [v0.15.0 release notes](docs/releases/v0.15.0.md), [ADR 0015](docs/adr/0015-generalized-abuse-protection.md), the
[generalized abuse-protection threat model](docs/security/abuse-protection-threat-model.md),
the [policy guide](docs/abuse-protection.md), the
[operations runbook](docs/operations/abuse-protection-observability.md), and
the committed [registration decision evidence](docs/performance/evidence/2026-08-17-registration-defer-f94ffa8.md).

## v0.16.0 release

The published v0.16.0 stabilization baseline completed the documented `/api/v1` compatibility boundary, repeatable PostgreSQL backup/restore and Flyway migration rehearsals, Redis and Kafka outage/recovery runbooks, OpenAPI/Postman/documentation drift review, dependency and secret scanning, SBOM/provenance evidence, clean-environment release rehearsal, protected finalization, and immutable publication verification. It added no new wallet, transfer, payment, identity-factor, or abuse-protection product feature and did not activate generalized registration protection.

Work was tracked by [issue #169](https://github.com/nursena-pc/payflow/issues/169), with finalization/publication closure tracked by [issue #186](https://github.com/nursena-pc/payflow/issues/186). Existing modular-monolith, security, privacy, simulated-money, and fail-closed boundaries remain in force.

## v1.0.0 active release-candidate development

The active `1.0.0-SNAPSHOT` line begins from exact v0.16.0 publication-record merge `7712c5ccbeeee3b9cefd3324c42270e71554ea17`. Work is tracked by [issue #189](https://github.com/nursena-pc/payflow/issues/189), with the development-start transition tracked by [issue #190](https://github.com/nursena-pc/payflow/issues/190). This checkpoint changes release/development state only and adds no product capability.

The v1 release candidate re-validates authentication and security lifecycle behavior, transaction/ledger/outbox/Kafka/DLQ guarantees, observability and bounded performance evidence, PostgreSQL backup/restore and Flyway migration rehearsals, Redis/Kafka recovery procedures, `/api/v1` compatibility, OpenAPI/Postman/documentation alignment, supply-chain evidence, and clean-environment release verification. Existing modular-monolith, simulated-money, registration `DEFER`, password-login limiter, fail-closed, privacy, and credential-redaction boundaries remain in force.

## Implemented API

The v0.16.0 stabilization line freezes the implementation-backed `/api/v1`
surface in the [API compatibility baseline](docs/api-v1-compatibility.md).
Breaking changes to the documented authentication, authorization, request,
response, status/error, idempotency, anti-enumeration, or single-use semantics
require an explicit reviewed compatibility checkpoint.

| Method | Endpoint | Authentication | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Registers a new user and stores a BCrypt password hash. |
| `POST` | `/api/v1/auth/login` | Public | Applies Redis-backed password protection; returns credentials when MFA is disabled or `202 MFA_REQUIRED` with an opaque challenge when MFA is enabled. |
| `POST` | `/api/v1/auth/mfa/challenges/confirm` | Public | Consumes one pending MFA login challenge after a valid TOTP or unused recovery-code proof and then issues access and refresh credentials. |
| `POST` | `/api/v1/auth/email-verification/requests` | Public | Accepts a generic verification request without disclosing account existence or eligibility. |
| `POST` | `/api/v1/auth/email-verification/confirm` | Public | Consumes one opaque credential and marks email ownership exactly once. |
| `POST` | `/api/v1/auth/password-recovery/requests` | Public | Accepts a generic recovery request without disclosing account existence or eligibility. |
| `POST` | `/api/v1/auth/password-recovery/confirm` | Public | Atomically replaces the BCrypt password hash and revokes active refresh sessions. |
| `GET` | `/api/v1/users/me/mfa` | Bearer | Returns the owning user's MFA lifecycle metadata without secret material. |
| `POST` | `/api/v1/users/me/mfa/enrollment` | Bearer + current password | Starts one pending TOTP enrollment and returns the provisioning secret once. |
| `POST` | `/api/v1/users/me/mfa/enrollment/confirm` | Bearer | Activates the pending authenticator after a valid six-digit TOTP proof and returns ten plaintext recovery codes once. |
| `POST` | `/api/v1/users/me/step-up/grants` | Bearer + enabled MFA proof | Issues one short-lived purpose-bound opaque step-up grant after a valid TOTP or unused recovery code. |
| `POST` | `/api/v1/users/me/mfa/recovery-codes/rotation` | Bearer + `recovery-code-rotation` step-up grant | Atomically replaces the complete recovery-code set and returns replacement plaintext once. |
| `DELETE` | `/api/v1/users/me/mfa` | Bearer + `mfa-disable` step-up grant | Disables MFA, removes recovery-code state, revokes active refresh sessions, and records audit evidence atomically. |
| `DELETE` | `/api/v1/users/me/mfa/enrollment` | Bearer | Cancels only a pending enrollment and deletes its protected secret row. |
| `POST` | `/api/v1/auth/refresh` | Public | Atomically rotates an active opaque refresh credential. |
| `POST` | `/api/v1/auth/logout` | Public | Idempotently revokes the refresh-token family represented by the submitted credential. |
| `POST` | `/api/v1/auth/logout-all` | Bearer JWT | Revokes every active refresh session owned by the authenticated user. |
| `GET` | `/api/v1/users/me` | Bearer JWT | Returns the authenticated user's safe profile fields. |
| `POST` | `/api/v1/wallets` | Bearer JWT | Opens a zero-balance wallet for the authenticated user. |
| `GET` | `/api/v1/wallets/me` | Bearer JWT | Returns the authenticated user's wallet summary. |
| `POST` | `/api/v1/wallets/me/top-ups` | Bearer JWT | Credits the authenticated user's wallet with a validated simulated amount. |
| `POST` | `/api/v1/transfers` | Bearer JWT | Creates an atomic and idempotent wallet-to-wallet transfer. |
| `GET` | `/api/v1/transactions/me` | Bearer JWT | Returns the authenticated user's paginated and filterable transaction history. |
| `GET` | `/api/v1/operations/kafka/dead-letters` | Bearer JWT with `role=ADMIN` | Lists paginated Kafka dead-letter operational metadata. |
| `GET` | `/api/v1/operations/kafka/dead-letters/{recordId}` | Bearer JWT with `role=ADMIN` | Returns the authorized operational details for one dead-letter record. |
| `POST` | `/api/v1/operations/kafka/dead-letters/{recordId}/replay` | Bearer JWT with `role=ADMIN` | Replays a claimable dead-letter record through the controlled lifecycle. |
| `POST` | `/api/v1/operations/kafka/dead-letters/{recordId}/discard` | Bearer JWT with `role=ADMIN` | Idempotently discards an eligible dead-letter record. |
| `GET` | `/api/v1/operations/kafka/dead-letter-command-audits` | Bearer JWT with `role=ADMIN` | Lists safe, paginated command-audit entries with optional filters. |
| `GET` | `/api/v1/operations/kafka/dead-letter-command-audits/{commandId}` | Bearer JWT with `role=ADMIN` | Returns the chronological audit timeline for one operator command. |
| `GET` | `/api/v1/system/health` | Configuration-dependent | Exposes the application health status. |

### Redis-backed login protection

`POST /api/v1/auth/login` evaluates normalized-identity and effective-client
counters in one Redis Lua script before user lookup or password verification.
The effective address is the direct servlet peer unless that peer belongs to an
explicitly configured trusted-proxy network. Only then may validated
`Forwarded` or `X-Forwarded-For` data influence the client identity. The default
policy allows five attempts per identity and twenty attempts per client in a
fixed fifteen-minute window.

Excessive attempts return `429 LOGIN_RATE_LIMIT_EXCEEDED` with a positive
`Retry-After` header. Redis decision or reset failures return fail-closed
`503 LOGIN_RATE_LIMIT_UNAVAILABLE`. Error responses remain generic and do not
reveal whether an identity exists.

A successful login resets only the identity counter; the client counter retains
its original expiration. Redis keys contain SHA-256 digests rather than raw
identity or client values.

See [Redis-Backed Login Rate Limiting](docs/login-rate-limiting.md) for policy,
configuration, metrics, reverse-proxy trust boundaries, and operational checks.
### Simulated wallet top-up

```http
POST /api/v1/wallets/me/top-ups
Authorization: Bearer <access-token>
Content-Type: application/json
```

Request body:

```json
{
  "amount": 250.00
}
```

Successful response:

```json
{
  "id": "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db",
  "balance": 250.00,
  "currency": "TRY",
  "status": "ACTIVE",
  "createdAt": "2026-07-15T12:00:00Z"
}
```

The currency is derived from the existing wallet rather than accepted from the client.

Relevant error outcomes include:

- `400 VALIDATION_FAILED`
- `401 Unauthorized`
- `404 WALLET_NOT_FOUND`
- `409 WALLET_CONCURRENT_UPDATE`
- `422 INVALID_MONEY_AMOUNT`
- `422 WALLET_NOT_ACTIVE`

### Wallet-to-wallet transfer

The source wallet is resolved from the authenticated JWT subject. Clients cannot provide or override the source wallet identifier.

```http
POST /api/v1/transfers
Authorization: Bearer <access-token>
Idempotency-Key: transfer-request-123
Content-Type: application/json
```

Request body:

```json
{
  "targetWalletId": "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db",
  "amount": 125.50
}
```

Successful response:

```json
{
  "transactionId": "b4077781-34f4-466f-8e61-b79ca906bc98",
  "sourceWalletId": "11111111-1111-1111-1111-111111111111",
  "targetWalletId": "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db",
  "amount": 125.50,
  "currency": "TRY",
  "status": "COMPLETED",
  "createdAt": "2026-07-15T18:30:00.123456Z",
  "completedAt": "2026-07-15T18:30:00.123456Z"
}
```

Transfer guarantees:

- the source wallet belongs to the authenticated user
- source debit and target credit execute in one PostgreSQL transaction
- every successful transfer creates one `DEBIT` and one `CREDIT` ledger entry
- debit and credit amounts must be equal
- repeated completed requests with the same key and payload return the existing transfer
- reusing the same key with a different payload returns a conflict
- concurrent duplicate requests create at most one financial movement
- persistence failure rolls back wallet, transaction, and ledger changes

Relevant error outcomes include:

- `400 VALIDATION_FAILED`
- `400 MISSING_IDEMPOTENCY_KEY`
- `401 Unauthorized`
- `404 WALLET_NOT_FOUND`
- `409 IDEMPOTENCY_KEY_CONFLICT`
- `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`
- `409 WALLET_CONCURRENT_UPDATE`
- `422 INSUFFICIENT_BALANCE`
- `422 SELF_TRANSFER_NOT_ALLOWED`
- `422 TRANSFER_CURRENCY_MISMATCH`
- `422 WALLET_NOT_ACTIVE`

## Postman collection

An executable Postman workflow is available under [`postman/`](postman/).

Import:

- `postman/PayFlow.postman_collection.json`
- `postman/PayFlow.mfa.postman_collection.json` for the MFA and step-up security workflow
- `postman/PayFlow.api-compatibility.postman_collection.json` for the five manually gated compatibility-coverage operations
- `postman/PayFlow.local.postman_environment.json`
- `postman/PayFlow.login-rate-limit.postman_collection.json` for the separate, deliberately disruptive login-limiter workflow

Select the **PayFlow Local** environment and run the standard application workflow folders in this order:

1. System
2. Authentication
3. Users
4. Wallets
5. Transfers
6. Transactions

The collection automatically generates unique test users, stores their JWT access tokens and wallet identifiers, creates an idempotent transfer, verifies completed-request replay, and queries transaction history.

Run the **Operations** folder separately. It is intentionally manual and requires a valid admin JWT in `operatorAccessToken`. Dead-letter mutation requests also require an existing UUID in `deadLetterRecordId`, while command timeline requests require a deliberately selected UUID in `auditCommandId`. These identifiers and the privileged token are not generated by the standard workflow or committed to the repository.

See [`postman/README.md`](postman/README.md) for detailed instructions.

### Transaction history

The endpoint resolves the wallet from the authenticated JWT subject. Clients cannot request another user's wallet history by supplying a wallet identifier.

```http
GET /api/v1/transactions/me?page=0&size=20&direction=OUTGOING&status=COMPLETED&from=2026-07-01T00:00:00Z&to=2026-08-01T00:00:00Z
Authorization: Bearer <access-token>
```

Supported query parameters:

| Parameter | Required | Description |
|---|---|---|
| `page` | No | Zero-based page number. Defaults to `0`. |
| `size` | No | Page size between `1` and `100`. Defaults to `20`. |
| `direction` | No | `INCOMING` or `OUTGOING`. |
| `status` | No | `PENDING`, `COMPLETED`, or `FAILED`. |
| `from` | No | Inclusive ISO-8601 instant. |
| `to` | No | Exclusive ISO-8601 instant. |

All filters are optional. When both date parameters are supplied, the endpoint uses the half-open interval `[from, to)`. Equal boundaries are valid and represent an empty date range.

Successful response:

```json
{
  "items": [
    {
      "transactionId": "b4077781-34f4-466f-8e61-b79ca906bc98",
      "type": "TRANSFER",
      "direction": "OUTGOING",
      "counterpartyWalletId": "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db",
      "amount": 125.50,
      "currency": "TRY",
      "status": "COMPLETED",
      "createdAt": "2026-07-16T10:00:00Z",
      "completedAt": "2026-07-16T10:00:01Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "hasNext": false,
  "hasPrevious": false
}
```

Transactions are ordered by `createdAt DESC, id DESC` so pagination remains deterministic when multiple transactions share the same timestamp.

The response intentionally excludes the transfer `Idempotency-Key`.

Relevant error outcomes include:

- `400 VALIDATION_FAILED`
- `401 Unauthorized`
- `404 WALLET_NOT_FOUND`

### Kafka dead-letter operations

The operations API is a deliberately narrow administration boundary. Spring Security grants `PAYFLOW_OPERATIONS` only when the authenticated JWT contains the exact claim `role=ADMIN`. A normal authenticated user receives `403 Forbidden`.

| Method | Endpoint | Successful outcome |
|---|---|---|
| `GET` | `/api/v1/operations/kafka/dead-letters?page=0&size=20&status=REPLAY_FAILED` | `200 OK` with a paginated record summary. |
| `GET` | `/api/v1/operations/kafka/dead-letters/{recordId}` | `200 OK` with the record details. |
| `POST` | `/api/v1/operations/kafka/dead-letters/{recordId}/replay` | `200 OK` with `status=REPLAYED`. |
| `POST` | `/api/v1/operations/kafka/dead-letters/{recordId}/discard` | `204 No Content`. |
| `GET` | `/api/v1/operations/kafka/dead-letter-command-audits?page=0&size=20` | `200 OK` with a paginated safe audit-entry response. |
| `GET` | `/api/v1/operations/kafka/dead-letter-command-audits/{commandId}` | `200 OK` with a chronological command timeline. |

Supported list statuses are `RECEIVED`, `REPLAYING`, `REPLAYED`, `REPLAY_FAILED`, and `DISCARDED`. Records are ordered by receive time descending and record identifier descending.

Query responses never return Kafka payload content or record keys. The details endpoint may include the recorded exception message, the last replay error, and replay-lease expiry for an authorized operator. Command audit rows are more restrictive: they contain the operator UUID derived from the JWT subject, record and command identifiers, command type, stage, outcome, and timestamp, but never payloads, record keys, JWTs, operator email addresses, exception messages, stack traces, or replay lease owners.

Each replay or discard command writes a correlated `ATTEMPTED` audit row before execution and a `COMPLETED` row afterward. PostgreSQL rejects updates and deletes against the command-audit table. If the initial audit row cannot be persisted, the command fails closed and is not executed.

The audit-list endpoint supports optional `commandId`, `operatorId`, `deadLetterRecordId`, `commandType`, `stage`, and `outcome` filters. Results use deterministic `occurredAt DESC, id DESC` ordering. The timeline endpoint returns `ATTEMPTED` followed by `COMPLETED` when present; an `ATTEMPTED`-only timeline remains a valid incomplete command history. Audit query responses expose only allowlisted identifiers, enums, safe error codes, and timestamps.

Important command outcomes include:

- `404` when the record does not exist
- `409` when the current lifecycle state does not allow the command
- `500` when an authorized command fails unexpectedly
- `502` when replay publication to Kafka fails
- `503` when replay resolution or command auditing cannot complete safely

Operator tokens are privileged credentials. Supply them only through a local Postman environment or another trusted secret store, and never commit an exported environment containing a live token.

## Local development

### Prerequisites

- JDK 21
- Maven 3.9+
- Docker with Docker Compose

### Start infrastructure

```bash
docker compose up -d postgres redis kafka
```

### Run tests

```bash
mvn clean verify
```

### Run the application

```bash
mvn spring-boot:run
```

The health endpoint is available at:

```text
GET http://localhost:8080/api/v1/system/health
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

### Run everything in Docker

```bash
docker compose --profile app up --build
```

## Database model

Flyway migrations define users, MFA authenticators, digest-only login challenges and recovery codes, refresh-token families and records, wallets, payment transactions, immutable ledger entries, transactional outbox records, processed Kafka events, Kafka dead-letter records, and append-only operator command audits.

Important database guarantees include:

- unique normalized user email addresses
- one effective MFA authenticator per user
- fixed-length and unique MFA login-challenge digests with one pending challenge per user
- fixed-length recovery-code digests with single-use consumption and no durable plaintext recovery credential
- fixed-length step-up grant digests with subject/purpose binding, short expiry, supersession, and single-use consumption
- fixed-length and globally unique refresh-token digests
- plaintext refresh tokens excluded from the persistence schema
- same-family refresh-token successor lineage
- paired token-consumption and successor metadata
- paired family-revocation timestamp and reason metadata
- token expiration bounded by family expiration
- one wallet per user
- non-negative wallet balances
- optimistic-lock wallet versions
- positive transaction and ledger amounts
- source-wallet-scoped idempotency-key uniqueness
- distinct source and target wallets for transfers
- ledger entry types restricted to `DEBIT` and `CREDIT`
- one ledger entry per transaction, wallet, and entry type
- constrained transactional-outbox lifecycle and indexed publication backlog
- idempotent processed-event identifiers
- constrained dead-letter replay and discard lifecycle transitions
- at most one audit row per command and stage
- database-level rejection of command-audit updates and deletes

JPA models store cross-module references as UUID values rather than direct entity associations. This keeps persistence coupling between modules explicit.

The source diagrams are stored in:

- `docs/diagrams/architecture.mmd`
- `docs/diagrams/er-diagram.mmd`

## Key design decisions

### Why PostgreSQL?

Transfers and ledger writes require strong relational constraints, transactional behavior, indexing, and predictable query semantics. PostgreSQL is the system of record.

### Why Redis?

Redis stores bounded, explicitly expiring login-attempt counters. Atomic Lua execution keeps identity and client decisions consistent across application instances, while PostgreSQL remains the source of truth for durable financial and refresh-session lifecycle state.

### Why Kafka?

Kafka decouples post-transfer processing from the transfer transaction. Transfer completion is persisted to a transactional outbox in the same PostgreSQL commit, then published by a leased and retryable worker. Consumers use durable processed-event records for idempotency, while failures are captured in a controlled dead-letter lifecycle.

### How are duplicate payments prevented?

Clients send an `Idempotency-Key`. PostgreSQL enforces uniqueness for the combination of source wallet and key.

When a completed transfer is requested again with the same key and payload, the application returns the existing transaction result without mutating balances or creating ledger entries again.

Using the same key with a different target wallet or amount returns `IDEMPOTENCY_KEY_CONFLICT`. A duplicate request that encounters an unfinished transaction returns `IDEMPOTENCY_REQUEST_IN_PROGRESS`.

### How are concurrent wallet updates protected?

Wallets use optimistic locking through a persisted version column. Conflicting updates fail rather than silently overwriting each other and are exposed as a stable `WALLET_CONCURRENT_UPDATE` conflict. The behavior is verified with two controlled concurrent transactions against PostgreSQL Testcontainers.

### What happens when a transfer fails?

Wallet mutation, payment-transaction persistence, ledger-entry persistence, and transactional-outbox persistence execute in one PostgreSQL transaction. An exception rolls back the complete unit of work, including wallet balances, optimistic-lock versions, and the outbox record.

Broker unavailability after the database commit does not roll back the completed transfer. The pending outbox record remains the source of truth and is retried by the publisher according to its leased lifecycle.

### How is the application tested?

- domain and application unit tests for business rules
- repository tests against PostgreSQL Testcontainers
- endpoint-to-database integration tests
- Redis and Kafka integration tests where those components participate
- CI verification on every pull request

## Git workflow

- Work begins from the latest protected `main` branch.
- Branches use `feat/<short-name>`, `fix/<short-name>`, `docs/<short-name>`, `chore/<short-name>`, or `release/<version>-<purpose>`.
- Features are divided into small, testable checkpoints and remain scoped to a linked issue.
- Commits follow Conventional Commits.
- Pull requests represent complete and reviewable value increments and target `main`.
- Required CI checks must pass, the reviewed PR HEAD must match the expected commit, and conflicts must be resolved before merging.
- Merge commits are retained to preserve pull-request and release provenance; published history is never rewritten.
- Merged feature and release branches are removed after merge verification so `main` and published tags remain the durable history.

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Disclaimer

PayFlow is an educational portfolio project. It is not certified for banking, payment processing, custody, or production financial use.

## Monitoring

PayFlow provides a local Prometheus and Grafana stack for transactional outbox observability, including a provisioned dashboard and operational alert rules.

See [Local Monitoring](docs/monitoring.md) for setup, validation, dashboards, alerts, and security considerations.
