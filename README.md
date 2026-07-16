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
- Spring MVC, Spring Data JPA, Hibernate, Spring Security
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
                 ↓
         Application use cases
                 ↓
             Domain model
```

This keeps domain rules independent from Spring and infrastructure while avoiding premature microservices. See [Architecture](docs/architecture.md) and the [ADR records](docs/adr).

## Current status

The repository foundation, identity flow, wallet management, wallet-to-wallet transfer, double-entry ledger, and authenticated transaction history are implemented.

Completed capabilities include:

- repository standards, contribution guidelines, and CI verification
- Docker-based PostgreSQL, Redis, and Kafka infrastructure
- versioned PostgreSQL schema management with Flyway
- secure-by-default Spring Security configuration
- user registration with normalized email addresses
- BCrypt password hashing
- user login with RSA-signed JWT access tokens
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

The next delivery focus is OpenAPI examples, a Postman collection, and the `v0.2.0` release, followed by transactional outbox and Kafka-based post-transfer processing. See the [roadmap](docs/roadmap.md).

## Implemented API

| Method | Endpoint | Authentication | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Registers a new user and stores a BCrypt password hash. |
| `POST` | `/api/v1/auth/login` | Public | Authenticates a user and returns an RSA-signed JWT access token. |
| `GET` | `/api/v1/users/me` | Bearer JWT | Returns the authenticated user's safe profile fields. |
| `POST` | `/api/v1/wallets` | Bearer JWT | Opens a zero-balance wallet for the authenticated user. |
| `GET` | `/api/v1/wallets/me` | Bearer JWT | Returns the authenticated user's wallet summary. |
| `POST` | `/api/v1/wallets/me/top-ups` | Bearer JWT | Credits the authenticated user's wallet with a validated simulated amount. |
| `POST` | `/api/v1/transfers` | Bearer JWT | Creates an atomic and idempotent wallet-to-wallet transfer. |
| `GET` | `/api/v1/transactions/me` | Bearer JWT | Returns the authenticated user's paginated and filterable transaction history. |
| `GET` | `/api/v1/system/health` | Configuration-dependent | Exposes the application health status. |

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

Flyway migrations define users, wallets, payment transactions, and immutable ledger entries.

Important database guarantees include:

- unique normalized user email addresses
- one wallet per user
- non-negative wallet balances
- optimistic-lock wallet versions
- positive transaction and ledger amounts
- source-wallet-scoped idempotency-key uniqueness
- distinct source and target wallets for transfers
- ledger entry types restricted to `DEBIT` and `CREDIT`
- one ledger entry per transaction, wallet, and entry type

JPA models store cross-module references as UUID values rather than direct entity associations. This keeps persistence coupling between the wallet, transaction, and ledger modules explicit.

The source diagrams are stored in:

- `docs/diagrams/architecture.mmd`
- `docs/diagrams/er-diagram.mmd`

## Key design decisions

### Why PostgreSQL?

Transfers and ledger writes require strong relational constraints, transactional behavior, indexing, and predictable query semantics. PostgreSQL is the system of record.

### Why Redis?

Redis will support bounded, explicitly expiring concerns such as login rate limiting, short-lived wallet summaries, refresh-token metadata, and selected idempotency lookups. PostgreSQL remains the source of truth.

### Why Kafka?

Kafka decouples post-transfer work such as notifications, audit enrichment, and activity updates. Transfer completion will be published through a transactional outbox so database commits are not coupled to broker availability.

### How are duplicate payments prevented?

Clients send an `Idempotency-Key`. PostgreSQL enforces uniqueness for the combination of source wallet and key.

When a completed transfer is requested again with the same key and payload, the application returns the existing transaction result without mutating balances or creating ledger entries again.

Using the same key with a different target wallet or amount returns `IDEMPOTENCY_KEY_CONFLICT`. A duplicate request that encounters an unfinished transaction returns `IDEMPOTENCY_REQUEST_IN_PROGRESS`.

### How are concurrent wallet updates protected?

Wallets use optimistic locking through a persisted version column. Conflicting updates fail rather than silently overwriting each other and are exposed as a stable `WALLET_CONCURRENT_UPDATE` conflict. The behavior is verified with two controlled concurrent transactions against PostgreSQL Testcontainers.

### What happens when a transfer fails?

Wallet mutation, payment-transaction persistence, and ledger-entry persistence execute in one PostgreSQL transaction. An exception rolls back the complete unit of work, including wallet balances and optimistic-lock versions.

Transactional outbox persistence will be added in a later milestone. Once implemented, the outbox record will join the same database transaction.

### How is the application tested?

- domain and application unit tests for business rules
- repository tests against PostgreSQL Testcontainers
- endpoint-to-database integration tests
- Redis and Kafka integration tests where those components participate
- CI verification on every pull request

## Git workflow

- Work begins from the latest `develop` branch.
- Branches use `feat/<short-name>`, `fix/<short-name>`, `docs/<short-name>`, or `chore/<short-name>`.
- Features are divided into small, testable checkpoints.
- Commits follow Conventional Commits.
- Pull requests represent complete and reviewable value increments.
- CI must pass and conflicts must be resolved before merging.
- Squash merge is preferred for a readable `develop` history.

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Disclaimer

PayFlow is an educational portfolio project. It is not certified for banking, payment processing, custody, or production financial use.
