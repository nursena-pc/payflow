# Delivery Roadmap

## Current delivery focus

The repository foundation, core identity flow, authenticated current-user profile, secure wallet creation, authenticated wallet retrieval, simulated top-up, and PostgreSQL optimistic-locking verification are complete.

The next delivery increment is wallet-to-wallet transfer processing with idempotency and double-entry ledger records.

## Milestone 0 — Repository foundation

- [x] Java 21 and Spring Boot 3.5 baseline
- [x] Modular-monolith package convention
- [x] PostgreSQL, Redis, and Kafka local infrastructure
- [x] Flyway baseline migration
- [x] CI, Dependabot, pull request, and issue templates
- [x] Wallet domain aggregate and unit tests

## Milestone 1 — Identity and authentication

- [x] User registration
- [x] Password hashing with BCrypt
- [x] User login
- [x] RSA-signed JWT access token
- [x] Authenticated current-user profile
- [x] Authentication integration tests
- [ ] Rotating refresh token and revocation
- [ ] ADMIN authorization
- [ ] Login rate limiting with Redis

## Milestone 2 — Wallet management

- [x] Open wallet for authenticated user
- [x] Enforce one wallet per user
- [x] Return a stable `409 Conflict` response for duplicate wallet creation
- [x] PostgreSQL persistence and integration tests with Testcontainers
- [x] Retrieve the authenticated user's wallet summary
- [x] Return a stable `WALLET_NOT_FOUND` response
- [x] Simulated top-up
- [x] Verify optimistic locking behavior with PostgreSQL concurrency tests

## Milestone 3 — Transfers and ledger

- [x] Transfer domain model and lifecycle
- [x] Payment-transaction persistence
- [x] `PENDING` to `COMPLETED` state transition
- [x] Authenticated transfer use case
- [x] Source wallet derived from JWT identity
- [x] `Idempotency-Key` HTTP contract
- [x] Source-wallet-scoped database uniqueness
- [x] Completed-request replay
- [x] Conflicting-payload detection
- [x] In-progress request conflict behavior
- [x] Atomic source-wallet debit and target-wallet credit
- [x] Double-entry ledger domain model
- [x] Immutable `DEBIT` and `CREDIT` persistence
- [x] Database constraints for transfer and ledger invariants
- [x] Rollback verification when ledger persistence fails
- [x] Concurrent duplicate-transfer verification
- [x] Authenticated endpoint-to-database integration test
- [ ] Transaction history
- [ ] Filtering by direction, status, and date range
- [ ] Pagination and stable sorting

## Milestone 4 — Event-driven processing

- [ ] Transactional outbox
- [ ] Versioned `wallet.transfer.completed` event
- [ ] Kafka publisher
- [ ] Notification and audit consumers
- [ ] Consumer idempotency
- [ ] Kafka integration tests

## Milestone 5 — Production readiness

- [ ] Refresh-token storage and key-rotation strategy
- [ ] Cache strategy and invalidation tests
- [ ] Structured logging and correlation IDs
- [ ] Prometheus metrics and Grafana dashboard
- [ ] API security hardening
- [ ] Performance test scenarios
- [ ] Architecture and ER diagram exports
- [ ] OpenAPI examples and Postman collection
- [ ] Release workflow and tagged release
