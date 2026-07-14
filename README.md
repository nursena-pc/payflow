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

Milestone 0 is complete:

- repository and CI foundation
- local PostgreSQL, Redis, and Kafka infrastructure
- Flyway core schema
- secure-by-default HTTP configuration
- wallet aggregate with money validation and domain tests

The next milestone is identity and JWT authentication. See the [roadmap](docs/roadmap.md).

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

The initial migration defines users, wallets, payment transactions, and immutable ledger entries. The wallet row includes an optimistic-lock version. Idempotency uniqueness is enforced for the source wallet and request key.

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

Clients send an `Idempotency-Key`. The database stores the key with a uniqueness constraint scoped to the source wallet. Repeated requests return the original result instead of creating another transfer.

### How are concurrent transfers protected?

Wallets use optimistic locking. Conflicting updates fail rather than silently overwriting each other. Concurrency behavior will be verified with PostgreSQL Testcontainers tests.

### What happens when a transfer fails?

Wallet mutation, transaction state, ledger entries, and outbox record creation execute in one database transaction. An exception rolls back the complete unit of work. Expected business failures are represented with stable error codes.

### How is the application tested?

- domain and application unit tests for business rules
- repository tests against PostgreSQL Testcontainers
- endpoint-to-database integration tests
- Redis and Kafka integration tests where those components participate
- CI verification on every pull request

## Git workflow

- Work begins from a GitHub issue with acceptance criteria.
- Branches use `feature/<issue>-name` or `fix/<issue>-name`.
- Commits follow Conventional Commits.
- Pull requests remain focused and require passing CI.
- Squash merge is preferred for a readable history.

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Disclaimer

PayFlow is an educational portfolio project. It is not certified for banking, payment processing, custody, or production financial use.
