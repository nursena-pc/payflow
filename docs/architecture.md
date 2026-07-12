# Architecture

PayFlow uses a modular monolith with hexagonal boundaries inside business modules.

## Dependency rule

`adapter -> application -> domain`

The domain model has no Spring, JPA, Kafka, Redis, or HTTP dependencies. Application services coordinate use cases and depend on output-port interfaces. Adapters implement infrastructure details.

## Package convention

```text
com.nursena.payflow
├── auth
├── user
├── wallet
│   ├── domain
│   ├── application
│   │   ├── port.in
│   │   └── port.out
│   └── adapter
│       ├── in.web
│       └── out.persistence
├── transaction
├── ledger
├── notification
├── audit
├── security
├── common
└── configuration
```

## Transaction strategy

The first implementation keeps wallet mutation, transaction state, ledger entries, and outbox event creation in one PostgreSQL transaction. Kafka publication will use a transactional outbox rather than publishing directly inside the database transaction.

## Concurrency strategy

- JPA optimistic locking on wallets through `@Version`
- retry policy only for explicitly classified optimistic-lock conflicts
- idempotency key uniqueness at database level
- deterministic wallet locking/order if pessimistic locking is introduced later
- ledger invariants enforced both in domain code and integration tests

## API conventions

- versioned routes under `/api/v1`
- RFC-style structured errors with stable application error codes
- DTOs at the HTTP boundary
- validation before use-case execution
- pagination for collection endpoints
- no JPA entity exposure
