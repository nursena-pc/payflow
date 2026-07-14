# Delivery Roadmap

## Milestone 0 — Repository foundation

- [x] Java 21 and Spring Boot 3.5 baseline
- [x] Modular-monolith package convention
- [x] PostgreSQL, Redis, and Kafka local infrastructure
- [x] Flyway baseline migration
- [x] CI, Dependabot, PR and issue templates
- [x] Wallet domain aggregate and unit tests

## Milestone 1 — Identity and authentication

- [ ] User registration
- [ ] Password hashing with BCrypt/Argon2 decision
- [ ] JWT access token
- [ ] Rotating refresh token
- [ ] USER and ADMIN authorization
- [ ] Login rate limiting with Redis
- [ ] Authentication integration tests

## Milestone 2 — Wallet management

- [ ] Open wallet for authenticated user
- [ ] Retrieve wallet summary
- [ ] Simulated top-up
- [ ] Optimistic locking behavior
- [ ] PostgreSQL repository tests with Testcontainers

## Milestone 3 — Transfers and ledger

- [ ] Transfer use case
- [ ] Idempotency-Key handling
- [ ] Transaction state machine
- [ ] Double-entry ledger
- [ ] Rollback and concurrency tests
- [ ] Filtering and pagination

## Milestone 4 — Event-driven processing

- [ ] Transactional outbox
- [ ] `wallet.transfer.completed` event
- [ ] Kafka publisher
- [ ] Notification and audit consumers
- [ ] Consumer idempotency
- [ ] Kafka integration tests

## Milestone 5 — Production readiness

- [ ] Cache strategy and invalidation tests
- [ ] Structured logging and correlation IDs
- [ ] Prometheus metrics and Grafana dashboard
- [ ] API security hardening
- [ ] Performance test scenarios
- [ ] Architecture and ER diagram exports
- [ ] Postman collection
- [ ] Release workflow and tagged release
