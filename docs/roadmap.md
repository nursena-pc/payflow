# Delivery Roadmap

## Current delivery focus

PayFlow v0.6.0 is published and the active development line uses
`0.7.0-SNAPSHOT`.

The v0.7.0 increment focuses on identity and session security. The goal is to
extend the existing RSA-signed access-token authentication with revocable,
rotating refresh-token sessions and bounded Redis-backed login protection.

The project remains a modular monolith. PostgreSQL remains the system of record,
Redis is used only for explicitly bounded and expiring concerns, and public API
changes must preserve stable security and error contracts.

## Delivered baseline through v0.6.0

### Repository and architecture

- [x] Java 21 and Spring Boot 3.5 baseline
- [x] Modular-monolith package convention
- [x] Inward-facing domain, application, and adapter dependencies
- [x] PostgreSQL, Redis, and Kafka local infrastructure
- [x] Flyway-managed database schema
- [x] CI, Dependabot, pull request, and issue templates
- [x] Conventional Commits and protected pull-request workflow
- [x] Tagged GitHub releases with executable JAR and SHA-256 assets

### Identity and authorization

- [x] User registration with normalized email addresses
- [x] BCrypt password hashing
- [x] User login
- [x] RSA-signed JWT access tokens
- [x] Authenticated current-user profile
- [x] Stable authentication and authorization error handling
- [x] ADMIN-derived `PAYFLOW_OPERATIONS` authorization boundary
- [x] Authentication and operations-security integration tests

### Wallets, transfers, and ledger

- [x] One wallet per authenticated user
- [x] Current-wallet retrieval
- [x] Simulated wallet top-up
- [x] Optimistic locking and PostgreSQL concurrency verification
- [x] Authenticated wallet-to-wallet transfers
- [x] Source identity derived from the JWT subject
- [x] Source-wallet-scoped idempotency
- [x] Atomic source debit and target credit
- [x] Immutable double-entry ledger persistence
- [x] Transaction rollback verification
- [x] Concurrent duplicate-transfer verification
- [x] Paginated and filterable transaction history
- [x] Deterministic transaction ordering
- [x] Endpoint-to-database integration tests

### Event-driven delivery and operations

- [x] Transactional outbox persistence
- [x] Versioned `wallet.transfer.completed` event
- [x] Leased and retryable Kafka publication
- [x] Idempotent event consumption
- [x] Kafka integration tests
- [x] Durable dead-letter intake
- [x] Replay lineage and controlled replay lifecycle
- [x] Explicit discard lifecycle
- [x] Operator-only dead-letter query and command APIs
- [x] Append-only replay and discard command auditing
- [x] Paginated audit investigation queries
- [x] Chronological command timelines
- [x] Safe-field response allowlists

### Documentation and observability

- [x] OpenAPI contracts and examples
- [x] Executable Postman collection
- [x] Prometheus metrics for Kafka delivery failures
- [x] Provisioned Grafana dashboards
- [x] Operational alert rules
- [x] Architecture and ER diagram sources
- [x] Release notes and upgrade guidance

## v0.7.0 — Identity and Session Security

### Product outcome

Authenticated users can maintain revocable sessions without receiving
long-lived bearer access tokens. Refresh-token rotation limits replay windows,
token-family reuse detection provides an incident response boundary, and login
rate limiting reduces automated credential attacks.

No plaintext refresh token, password, JWT, authorization header, or equivalent
credential may be persisted, logged, returned through administrative APIs, or
included in metrics.

### Increment 1 — Architecture and security contracts

- [ ] Record the refresh-session architecture and threat model in an ADR
- [ ] Define access-token and refresh-token responsibilities
- [ ] Define token-family lifecycle states and invariants
- [ ] Define expiration, rotation, revocation, and reuse-detection semantics
- [ ] Define stable public error codes and HTTP outcomes
- [ ] Define safe logging, metric, and audit-field allowlists
- [ ] Define clock and token-generation ports for deterministic tests

### Increment 2 — Durable refresh-session persistence

- [ ] Add a Flyway migration for refresh-token families and token records
- [ ] Persist only cryptographic token hashes
- [ ] Store family, user, creation, expiration, replacement, and revocation metadata
- [ ] Enforce database constraints for family and rotation invariants
- [ ] Add indexes for active-token lookup and user-session revocation
- [ ] Add PostgreSQL repository integration tests
- [ ] Verify that plaintext credentials never enter persistence

### Increment 3 — Token issuance and atomic rotation

- [ ] Extend successful login with an access and refresh token pair
- [ ] Add a refresh endpoint with an explicit request contract
- [ ] Rotate refresh tokens exactly once
- [ ] Replace the consumed token atomically
- [ ] Reject expired, revoked, malformed, and unknown tokens deterministically
- [ ] Handle concurrent refresh requests without issuing multiple valid successors
- [ ] Add endpoint-to-database integration and concurrency tests

### Increment 4 — Reuse detection and session revocation

- [ ] Detect reuse of an already rotated refresh token
- [ ] Revoke the complete token family after confirmed reuse
- [ ] Add current-session logout
- [ ] Add all-session logout for the authenticated user
- [ ] Keep logout behavior idempotent
- [ ] Add safe security metrics for rotation, rejection, reuse, and revocation
- [ ] Verify that operational data exposes no token material

### Increment 5 — Redis-backed login protection

- [ ] Define a bounded login-attempt policy
- [ ] Apply limits using normalized login identity and trusted client context
- [ ] Use atomic Redis operations with explicit expiration
- [ ] Return a stable `429 Too Many Requests` contract
- [ ] Avoid revealing whether an account exists
- [ ] Define safe behavior when Redis is unavailable
- [ ] Add Redis integration, expiration, and concurrency tests

### Increment 6 — Public contracts and release readiness

- [ ] Update OpenAPI authentication contracts and examples
- [ ] Update the executable Postman authentication workflow
- [ ] Document refresh, logout, reuse, and rate-limit behavior
- [ ] Add regression tests for existing access-token-only endpoints
- [ ] Verify authentication responses exclude credential internals
- [ ] Run full Maven, security, migration, and API-contract verification
- [ ] Publish v0.7.0 release notes, JAR, and SHA-256 checksum

## Explicit v0.7.0 non-goals

The following work is valuable but is not part of the v0.7.0 commitment:

- external OAuth or OpenID Connect identity providers
- social login
- multi-factor authentication
- password-reset and email-verification workflows
- browser or mobile user interfaces
- device fingerprinting
- generalized API-wide rate limiting
- production KMS or HSM integration
- microservice extraction
- wallet-summary caching
- unrelated dependency modernization

These items require separate milestones and threat models rather than being
silently added to the session-security increment.

## v0.7.0 release exit criteria

The release is ready only when:

- [ ] all committed v0.7.0 issues are closed
- [ ] database migrations are backward-safe and verified with PostgreSQL
- [ ] refresh-token rotation is atomic under concurrent requests
- [ ] confirmed token reuse revokes the complete family
- [ ] current-session and all-session logout behavior is verified
- [ ] login rate limiting is verified against Redis
- [ ] no plaintext refresh token or other credential is persisted or logged
- [ ] OpenAPI and Postman contracts match the implementation
- [ ] all automated tests and pull-request checks pass
- [ ] release documentation, executable JAR, and SHA-256 checksum are published

## Future candidates

Potential increments after v0.7.0 include:

- signing-key rotation and external key-management integration
- structured JSON logging and request correlation
- performance and load-test scenarios
- cache strategy and invalidation verification
- password recovery and verified-email workflows
- multi-factor authentication
- broader operational-security dashboards
