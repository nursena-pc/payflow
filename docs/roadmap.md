# Delivery Roadmap

## Current delivery focus

PayFlow v0.8.0 is the latest tagged baseline. The active development line uses
`0.9.0-SNAPSHOT`.

The v0.9.0 increment focuses on distributed Redis-backed login protection and
release readiness for the completed refresh-session security lifecycle. PayFlow
remains a modular monolith. PostgreSQL is the system of record; Redis is used
only for bounded, explicitly expiring abuse-control state.

## Delivered platform baseline

### Repository and architecture

- [x] Java 21 and Spring Boot 3.5 baseline
- [x] Modular-monolith package convention
- [x] Inward-facing domain, application, and adapter dependencies
- [x] PostgreSQL, Redis, and Kafka local infrastructure
- [x] Flyway-managed database schema
- [x] CI, Dependabot, pull-request, and issue templates
- [x] Conventional Commits and protected pull-request workflow
- [x] Tagged releases with executable JAR and SHA-256 assets

### Identity, authorization, and sessions

- [x] User registration with normalized email addresses
- [x] BCrypt password hashing
- [x] RSA-signed JWT access tokens
- [x] Opaque refresh credentials persisted only as SHA-256 digests
- [x] Atomic refresh-token rotation
- [x] Concurrent rotation protection
- [x] Refresh-token reuse detection and family revocation
- [x] Current-session logout
- [x] Authenticated all-session logout
- [x] Durable all-session revocation across application restarts
- [x] Stable authentication and authorization error contracts
- [x] ADMIN-derived `PAYFLOW_OPERATIONS` authorization boundary

### Wallets, transfers, ledger, and events

- [x] One wallet per authenticated user
- [x] Simulated top-up and current-wallet retrieval
- [x] Optimistic locking and PostgreSQL concurrency verification
- [x] Authenticated, atomic wallet-to-wallet transfers
- [x] Source-wallet-scoped idempotency
- [x] Immutable double-entry ledger persistence
- [x] Transaction rollback and concurrent duplicate verification
- [x] Paginated and filterable transaction history
- [x] Transactional outbox and retryable Kafka publication
- [x] Idempotent event consumption
- [x] Durable dead-letter intake, replay, and discard lifecycle
- [x] Append-only operator command auditing

### Documentation and observability

- [x] OpenAPI contracts and examples
- [x] Executable standard Postman workflow
- [x] Dedicated login rate-limit Postman workflow
- [x] Prometheus metrics, Grafana dashboards, and alert rules
- [x] Architecture and ER diagram sources
- [x] Security and operations documentation

## v0.9.0 — Redis-Backed Login Protection

### Product outcome

Login attempts are evaluated consistently across application instances without
revealing whether an account exists. The implementation uses bounded fixed
windows, hashed Redis keys, atomic Lua updates, low-cardinality metrics, and
fail-closed behavior when Redis cannot make a safe decision.

### Increment 1 — Redis foundation

- [x] Define identity and client rate-limit ports and domain contracts
- [x] Add validated configuration properties with secure defaults
- [x] Hash normalized identity and client values before key construction
- [x] Implement atomic Redis Lua evaluation
- [x] Apply expiration only when a fixed-window key is created
- [x] Add bounded metric tags and credential-free security events

### Increment 2 — Authentication integration

- [x] Apply the limiter before user lookup and password verification
- [x] Preserve generic invalid-credential responses below the threshold
- [x] Return stable `429 LOGIN_RATE_LIMIT_EXCEEDED`
- [x] Derive a positive `Retry-After` value from Redis TTL
- [x] Reset only the identity counter after successful login
- [x] Preserve the client counter until its original expiration
- [x] Return fail-closed `503 LOGIN_RATE_LIMIT_UNAVAILABLE`
- [x] Keep direct servlet peer addressing as the explicit trust boundary

### Increment 3 — Verification

- [x] Unit-test configuration, hashing, decisions, metrics, and error mapping
- [x] Verify identity and client thresholds with real Redis
- [x] Verify fixed-window expiration is not refreshed
- [x] Verify identity-only reset after successful authentication
- [x] Verify atomic Lua behavior under concurrent requests
- [x] Verify HTTP `429` and `Retry-After`
- [x] Verify Redis outage produces fail-closed HTTP `503`
- [x] Run the complete Maven verification suite

### Increment 4 — Public and operational contracts

- [x] Update OpenAPI authentication responses
- [x] Add a dedicated, credential-free Postman verification collection
- [x] Add Postman collection contract tests
- [x] Document thresholds, TTL, metrics, logs, and outage behavior
- [x] Document reverse-proxy and forwarded-address trust boundaries
- [x] Update the root project documentation

### Increment 5 — Pull request and release readiness

- [ ] Synchronize the feature branch with the latest `origin/main`
- [ ] Open the pull request linked to issue #98
- [ ] Pass protected-branch CI and review checks
- [ ] Merge through the protected pull-request workflow
- [ ] Publish v0.9.0 release notes
- [ ] Publish the executable JAR and SHA-256 checksum
- [ ] Tag the verified release commit as `v0.9.0`

## Explicit v0.9.0 non-goals

- external OAuth or OpenID Connect identity providers
- social login
- multi-factor authentication
- password-reset and email-verification workflows
- device fingerprinting
- generalized API-wide rate limiting
- production KMS or HSM integration
- unrestricted trust of forwarded client-address headers
- microservice extraction
- wallet-summary caching
- unrelated dependency modernization

These items require separate issues, milestones, and threat models rather than
being silently added to the login-protection increment.

## v0.9.0 release exit criteria

The release is ready only when:

- [x] refresh-token rotation is atomic under concurrent requests
- [x] confirmed token reuse revokes the complete family
- [x] current-session and all-session logout behavior is verified
- [x] login rate limiting is verified against real Redis
- [x] plaintext refresh tokens and authentication credentials are excluded from persistence and logs
- [x] OpenAPI and Postman contracts match the implementation
- [x] all local automated tests pass
- [ ] the pull request is approved and merged
- [ ] protected-branch CI passes on the merge candidate
- [ ] release notes, executable JAR, and SHA-256 checksum are published

## Future candidates

Potential increments after v0.9.0 include:

- signing-key rotation and external key-management integration
- trusted reverse-proxy client-address resolution
- structured JSON logging and request correlation
- load and performance verification
- password recovery and verified-email workflows
- multi-factor authentication
- broader operational-security dashboards
- generalized API abuse protection
