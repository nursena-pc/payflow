# Architecture

PayFlow uses a modular monolith with hexagonal boundaries inside business modules.

The system is deployed as a single Spring Boot application, while business capabilities remain separated by package, port, and dependency boundaries.

## Dependency rule

```text
adapter -> application -> domain
```

The domain model has no Spring, JPA, Kafka, Redis, or HTTP dependencies.

Application services coordinate use cases and depend on input and output ports. Adapters translate between the application core and delivered infrastructure concerns such as HTTP, PostgreSQL, Redis, Kafka, SMTP, security, and observability.

## Package convention

The current top-level capability packages are source-backed:

```text
com.nursena.payflow
|-- abuseprotection
|-- clientcontext
|-- common
|-- configuration
|-- eventprocessing
|-- ledger
|-- maildelivery
|-- observability
|-- outbox
|-- transaction
|-- user
`-- wallet
```

Business capabilities use domain, application/port, and adapter boundaries where the capability requires them; infrastructure-focused packages keep infrastructure concerns outside domain code. The package inventory describes delivered code rather than reserving empty top-level packages for speculative future capabilities.

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

- registration and normalized email identity
- password-hash persistence and authentication
- email-verification and password-recovery credential lifecycles
- opaque refresh-session rotation, reuse detection, and family revocation
- RSA-signed JWT creation with active/previous signing-key overlap
- authenticated current-user profile
- TOTP enrollment, MFA login challenges, recovery codes, and step-up grants
- the separate Redis-backed password-login limiter compatibility boundary

JWT key resources, PEM parsing, Nimbus selection, and deployment rotation stay inside the outbound security adapter. Application and domain code depend on application-facing token and persistence ports rather than key-file or servlet details.

### Abuse-protection module

The `abuseprotection` capability owns the generalized bounded policy and Redis enforcement used by the reviewed account-action, MFA-challenge, and step-up flows. Redis stores only expiring derived control state and low-cardinality decision data. Registration remains outside generalized abuse-protection wiring under the evidence-backed `DEFER` decision, and the password-login limiter remains a separate compatibility contract.

### Mail-delivery module

The `maildelivery` capability owns protected account-action mail persistence and SMTP dispatch. Provider-ready verification/recovery content is encrypted before PostgreSQL persistence, leased for delivery after commit, decrypted only in memory, and erased after terminal outcomes.

### Outbox and event-processing modules

The `outbox` capability persists transfer-completed publication intent in the same PostgreSQL unit of work as the completed transfer and publishes after commit through a leased retryable Kafka publisher. The `eventprocessing` capability owns idempotent event consumption, durable dead-letter intake, controlled replay/discard lifecycle handling, and append-only operator command audit evidence.

Kafka delivery is intentionally treated as at-least-once. A producer acknowledgement timeout can leave publication outcome ambiguous, so downstream PostgreSQL idempotency is the durable duplicate-processing boundary rather than an exactly-once broker claim.

### Observability module

The `observability` capability owns trustworthy request correlation, bounded request-completion events, structured logging/redaction support, and adapter-side operational metrics. Credentials, raw client addresses, request bodies, financial values, and other sensitive high-cardinality values remain outside logs and metric labels.

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

Transactional outbox persistence is implemented as part of the current transfer transaction.

A completed transfer and its publication intent are persisted in the same PostgreSQL unit of work. Kafka publication happens after commit through a separate leased, retryable publisher; the transfer use case does not publish directly to Kafka inside the database transaction.

PostgreSQL remains the system of record for transfer, ledger, outbox, dead-letter, replay, and processing evidence where designed. Kafka delivery has an at-least-once delivery boundary: an acknowledgement timeout can make a send outcome ambiguous and a later retry may produce duplicate broker delivery. Durable processed-event and audit/idempotency state prevents a second financial movement or a second effective processing result.

Redis is not a system of record. It remains limited to bounded, explicitly expiring abuse-control state, including the separate password-login limiter and generalized abuse-protection decisions.

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

/api/v1/operations/** remains restricted to the application-owned PAYFLOW_OPERATIONS authority derived from the exact administrative JWT role contract. Registration remains outside generalized abuse-protection wiring under the reviewed DEFER decision; the password-login limiter remains a separate compatibility contract with unchanged fail-closed behavior.

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

## Protected account-action mail delivery

Email-verification and password-recovery workflows depend on the user module's `AccountActionMailPort`. The mail-delivery adapter renders transactional content, protects provider-ready bodies with AES-256-GCM, and persists a dedicated outbox row in the credential transaction. A separate leased dispatcher decrypts only in memory and invokes SMTP after commit. Terminal outcomes erase protected content. See ADR 0013 for delivery semantics and duplicate-risk boundaries.
