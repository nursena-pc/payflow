# Architecture

PayFlow uses a modular monolith with hexagonal boundaries inside business modules.

The system is deployed as a single Spring Boot application, while business capabilities remain separated by package, port, and dependency boundaries.

## Dependency rule

```text
adapter -> application -> domain
```

The domain model has no Spring, JPA, Kafka, Redis, or HTTP dependencies.

Application services coordinate use cases and depend on input and output ports. Adapters translate between the application core and infrastructure concerns such as HTTP, PostgreSQL, security, and future messaging integrations.

## Package convention

```text
com.nursena.payflow
├── user
│   ├── domain
│   ├── application
│   │   ├── port.in
│   │   └── port.out
│   └── adapter
│       ├── in.web
│       └── out
├── wallet
│   ├── domain
│   ├── application
│   │   ├── port.in
│   │   └── port.out
│   └── adapter
│       ├── in.web
│       └── out.persistence
├── transaction
│   ├── domain
│   ├── application
│   │   ├── port.in
│   │   └── port.out
│   └── adapter
│       ├── in.web
│       └── out.persistence
├── ledger
│   ├── domain
│   ├── application
│   │   └── port.out
│   └── adapter
│       └── out.persistence
├── clientcontext
│   ├── domain
│   └── adapter
│       └── in.web
├── notification
├── audit
├── common
└── configuration
```

Packages that represent future capabilities may remain empty until a concrete use case requires them. Infrastructure is not added only to demonstrate a technology.

## Module responsibilities

### Client-context module

The client-context module owns:

- literal-only IPv4 and IPv6 normalization
- trusted-proxy CIDR validation and containment
- deterministic forwarding-chain resolution
- direct-peer fallback decisions
- bounded, address-free resolution metrics

Servlet request access, forwarding-header parsing, and Micrometer wiring remain
inside the inbound web adapter. The user module consumes only the normalized
effective address through `ClientAddressResolver`.

### User module

The user module owns:

- registration
- normalized email identity
- password-hash persistence
- authentication
- RSA-signed JWT creation
- adapter-local signing-key retrieval and startup validation
- stable JWT key identifiers and active/previous verification overlap
- authenticated current-user profile

JWT key resources, PEM parsing, Nimbus selection, and deployment rotation stay
inside the outbound security adapter. Application and domain code depend only
on the existing access-token generation port. Production key rings contain one
active signer and at most one verification-only previous key; verification is
pinned to RS256 and requires a configured `kid`.

### Wallet module

The wallet module owns:

- wallet lifecycle
- wallet currency and status
- current balance
- credit and debit rules
- insufficient-balance validation
- optimistic-lock versioning
- one-wallet-per-user enforcement

### Transaction module

The transaction module owns:

- transfer request orchestration
- payment-transaction lifecycle
- idempotency evaluation
- transfer request identity
- `PENDING` and `COMPLETED` states
- stable transfer results
- authenticated transaction-history queries
- incoming and outgoing direction mapping
- transaction-status and date-range filtering
- pagination and deterministic ordering

### Ledger module

The ledger module owns:

- immutable ledger entries
- debit and credit entry types
- double-entry balance invariants
- persistence of accounting records

The transaction module coordinates the use case, but it does not absorb wallet or ledger domain responsibilities.

## Transaction history read path

Transaction history is implemented as an application-level read use case rather than exposing Spring Data or JPA models through the HTTP boundary.

The read flow is:

1. obtain the authenticated user identifier from the verified JWT subject
2. resolve the wallet owned by that user
3. construct an application-level transaction-history filter
4. query payment transactions where the wallet is either the source or target
5. derive `OUTGOING` or `INCOMING` from the wallet's position in the transaction
6. map the opposite wallet as the counterparty
7. return an application-owned paginated result
8. map the result to HTTP response DTOs

The persistence query supports optional filtering by:

- transaction direction
- transaction status
- inclusive `from` instant
- exclusive `to` instant

Results use the deterministic ordering:

```text
createdAt DESC, id DESC
```

The UUID tie-breaker keeps pagination predictable when multiple transactions share the same creation timestamp.

Spring Data `Page`, JPA entities, and transfer idempotency keys do not cross the application or HTTP boundaries.

## Transaction strategy

Wallet mutation, payment-transaction persistence, transaction-state completion, and double-entry ledger persistence execute in one PostgreSQL transaction.

The current transfer unit of work is:

1. resolve the authenticated user's source wallet
2. construct and validate the requested monetary amount
3. normalize the `Idempotency-Key`
4. evaluate whether an existing transaction already owns the key
5. return the existing result when a completed matching request is replayed
6. reject conflicting or unfinished duplicate requests
7. resolve the target wallet
8. validate wallet currency compatibility
9. create a `PENDING` payment transaction
10. debit the source wallet
11. credit the target wallet
12. persist the payment transaction
13. persist both wallet mutations
14. create one debit and one credit ledger entry
15. persist the double-entry ledger
16. mark the payment transaction as `COMPLETED`

Any exception propagating from this unit of work rolls back:

- source-wallet balance changes
- target-wallet balance changes
- wallet optimistic-lock version changes
- payment-transaction persistence
- ledger-entry persistence

The rollback behavior is verified against PostgreSQL by forcing ledger persistence to fail after wallet and transaction persistence have begun.

## Transactional outbox strategy

Transactional outbox persistence is planned for a later milestone and is not part of the current transfer transaction.

When implemented, the outbox record will be written in the same PostgreSQL transaction as the completed transfer. Kafka publication will happen after commit through a separate publisher.

The transfer use case will not publish directly to Kafka inside the database transaction.

## Concurrency strategy

PayFlow uses multiple complementary controls rather than relying on a single application-level check.

### Wallet updates

- Wallet rows use JPA optimistic locking through `@Version`.
- Persistence adapters translate classified optimistic-lock failures into a stable `WALLET_CONCURRENT_UPDATE` application exception.
- Conflicting updates fail instead of silently overwriting committed balances.
- Real PostgreSQL concurrency tests verify lost-update protection.
- Automatic retry is not currently applied to financial mutations.
- Wallet updates follow deterministic ordering to reduce inconsistent lock acquisition behavior.

### Transfer idempotency

- Clients provide an `Idempotency-Key`.
- PostgreSQL enforces uniqueness for `source_wallet_id + idempotency_key`.
- Application-level lookup provides replay and business-error behavior.
- The database constraint remains the final authority when concurrent requests both pass the initial lookup.
- Completed requests with the same payload replay the existing transaction.
- Reusing the key with a different payload returns `IDEMPOTENCY_KEY_CONFLICT`.
- Encountering an unfinished transaction returns `IDEMPOTENCY_REQUEST_IN_PROGRESS`.
- Controlled concurrent integration tests verify that duplicate requests create at most one financial movement.

### Ledger consistency

- Double-entry invariants are validated by the domain model.
- PostgreSQL check constraints restrict valid entry types.
- PostgreSQL uniqueness prevents duplicate transaction-wallet-entry-type combinations.
- Integration tests verify one debit and one credit entry for a completed transfer.
- Rollback tests verify that partial ledger state cannot survive a failed transfer.

## Persistence boundaries

Cross-module persistence references are represented as UUID identifiers rather than direct JPA entity associations.

For example:

- a payment transaction stores source and target wallet identifiers
- a ledger entry stores payment-transaction and wallet identifiers
- transaction entities do not contain `@ManyToOne` references to wallet entities
- ledger entities do not depend on transaction or wallet JPA entities

This keeps module coupling explicit and prevents persistence mappings from becoming hidden domain dependencies.

Repository ports expose domain-oriented operations. JPA repositories and entities remain adapter details.

## Transfer consistency guarantees

A completed transfer must satisfy all of the following:

- source and target wallets exist
- source wallet belongs to the authenticated user
- source and target wallets are different
- source and target wallets are active
- source and target currencies match
- amount is positive
- amount uses supported monetary precision
- source wallet has sufficient balance
- source debit equals target credit
- exactly one debit and one credit ledger entry represent the movement
- both ledger entries reference the same payment transaction
- debit and credit entries reference different wallets
- transaction status is `COMPLETED`
- replay does not create additional balance or ledger mutations

These guarantees are enforced through a combination of:

- domain invariants
- application orchestration
- PostgreSQL transactions
- database constraints
- optimistic locking
- idempotency uniqueness
- unit tests
- persistence tests
- real PostgreSQL integration tests

## Security boundaries

Protected endpoint identity is derived from the verified JWT subject.

Clients cannot supply the source user or source wallet identifier for:

- current-user profile access
- wallet creation
- current-wallet retrieval
- wallet top-up
- wallet-to-wallet transfer
- transaction-history retrieval

This prevents a client from accessing or changing another user's data by supplying a different user or wallet identifier in request input.

Spring Security uses a deny-by-default policy. New routes remain inaccessible until they are explicitly classified as public or authenticated.

### Trusted client-address boundary

Client-scoped login protection does not trust forwarding headers merely because
they are present. The servlet transport peer is parsed first. If that address is
not inside an explicitly configured trusted-proxy CIDR, `Forwarded` and
`X-Forwarded-For` are ignored and the direct peer remains the effective client.

For a trusted direct peer, the inbound adapter selects `Forwarded` before
`X-Forwarded-For`, validates bounded literal-only chain data, and walks the chain
from right to left until it finds the first untrusted address. Malformed,
obfuscated, oversized, or excessive-hop input falls back to the direct peer.

The resolved address crosses into the user login adapter only as a normalized
value. Redis stores only its SHA-256 digest. Micrometer receives only bounded
`source` and `outcome` enums; raw client addresses and forwarding values do not
enter metric labels or logs.

## API conventions

- routes are versioned under `/api/v1`
- protected routes use Bearer JWT authentication
- DTOs exist at the HTTP boundary
- application commands and results remain independent from HTTP
- request validation runs before use-case execution
- JPA entities are never exposed through API responses
- business errors use stable application error codes
- validation errors include field violations
- collection endpoints use pagination and deterministic sorting
- financial mutation endpoints define explicit conflict behavior
- transaction-history date ranges use inclusive `from` and exclusive `to` boundaries
- invalid pagination, enum, and timestamp parameters return stable validation responses

## Testing strategy

The test suite uses several complementary levels:

- domain tests for pure financial rules
- application-service unit tests for use-case coordination
- MockMvc slice tests for HTTP, validation, security, and error mapping
- persistence-adapter tests for JPA mapping and constraint translation
- Testcontainers tests against real PostgreSQL
- PostgreSQL transaction-history tests for filtering, isolation, pagination, and deterministic ordering
- controlled concurrency tests
- rollback and failure-path tests
- authenticated endpoint-to-database integration tests

PostgreSQL is used instead of an in-memory substitute for behavior involving:

- named constraints
- transaction rollback
- optimistic locking
- UUID persistence
- timestamp precision
- concurrent unique-key races
