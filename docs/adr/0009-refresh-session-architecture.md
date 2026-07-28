# ADR 0009: Use opaque rotating refresh sessions

- Status: Accepted
- Date: 2026-07-26

## Context

PayFlow currently authenticates users through:

```text
POST /api/v1/auth/login
```

A successful login returns one RSA-signed JWT access token. The token:

- uses the configured PayFlow issuer
- uses the authenticated user UUID as the `sub` claim
- carries the user's email address and domain role
- expires after the configured short lifetime
- is validated by Spring Security resource-server support
- is not persisted or placed on a server-side denylist

The HTTP security boundary is stateless and deny-by-default. Registration and
login are public, while customer and operations endpoints require a verified
access token.

This design avoids durable session state, but it requires users to submit their
credentials again after access-token expiration. Increasing the access-token
lifetime would reduce login frequency while increasing the useful lifetime of a
stolen bearer credential.

PayFlow v0.7.0 requires revocable sessions without turning access tokens into
long-lived credentials. The design must provide:

- short-lived JWT access tokens
- opaque refresh tokens
- single-use refresh-token rotation
- token-family reuse detection
- current-session logout
- all-session logout
- deterministic concurrent-refresh behavior
- durable PostgreSQL correctness
- safe errors, logs, metrics, and diagnostic events

Refresh tokens are credentials. Plaintext refresh tokens must not be persisted,
logged, measured, traced, audited, or exposed through administrative APIs.

## Decision

PayFlow will introduce server-side refresh sessions based on opaque,
single-use refresh tokens and durable PostgreSQL state.

The design uses two related concepts:

- a refresh-token family representing one authenticated client session
- refresh-token records representing every token issued in that family

One successful login creates one family and one initial token record.

Each successful refresh consumes one active token and creates exactly one
successor token in the same family.

PostgreSQL is the authoritative source of refresh-session lifecycle state.

Redis is not part of refresh-token correctness, rotation, reuse detection, or
revocation.

## Credential responsibilities

### Access tokens

Access tokens remain RSA-signed JWT bearer tokens.

They are used only to authorize protected API requests.

Access tokens:

- remain short-lived
- are not persisted in PostgreSQL
- are not placed on a server-side denylist
- are verified through signature, issuer, and expiration validation
- derive authenticated identity from the verified `sub` claim
- carry the PayFlow role required for authority mapping

Revoking a refresh-token family does not immediately invalidate access tokens
that were already issued. Those access tokens may remain usable until their
normal expiration.

This residual validity window is accepted for v0.7.0.

Access-token denylisting and online token introspection are outside this
decision.

### Refresh tokens

Refresh tokens are opaque random credentials.

A refresh token:

- is not a JWT
- contains no user, role, family, or expiration claims
- is not accepted as a Bearer credential for business endpoints
- is returned only by successful login and refresh operations
- is submitted only to refresh-session endpoints
- is single-use for rotation
- is never persisted in plaintext
- is never logged, measured, traced, or exposed operationally

The initial API transport uses JSON request and response bodies.

Browser-cookie transport is not defined by this ADR. Cookie transport would
require a separate decision covering CSRF, SameSite, Secure, domain, and path
rules.

## Token generation and hashing

Each refresh token contains at least 256 bits of entropy produced by a
cryptographically secure random generator.

Its external representation uses canonical Base64 URL encoding without
padding.

When PayFlow receives a refresh token, it:

1. validates the encoded shape
2. decodes it using strict Base64 URL rules
3. verifies the expected decoded byte length
4. calculates SHA-256 over the decoded random bytes
5. uses only the digest for persistence lookup

PostgreSQL stores the fixed-length binary digest, not the encoded token.

A deterministic SHA-256 digest is appropriate because refresh tokens have
machine-generated 256-bit entropy. Password hashing algorithms such as BCrypt
and Argon2 are not used for token lookup because their salted outputs prevent
indexed equality lookup.

Refresh-token digests remain sensitive internal credential verifiers. They must
not appear in logs, responses, metrics, traces, audit records, or operational
APIs.

## Refresh-session data model

### Token family

A refresh-token family represents one authenticated client session.

The durable family record contains at least:

- family UUID
- user UUID
- creation timestamp
- absolute expiration timestamp
- optional revocation timestamp
- optional fixed revocation reason

Initial revocation reasons are:

- `CURRENT_SESSION_LOGOUT`
- `ALL_SESSIONS_LOGOUT`
- `REUSE_DETECTED`
- `USER_ACCOUNT_UNAVAILABLE`
- `ADMINISTRATIVE_REVOCATION`

Administrative revocation is reserved for future trusted internal use. This ADR
does not introduce a public administrative session endpoint.

A family is active only when:

- it has not been revoked
- its absolute expiration is later than the shared clock time
- the owning user remains eligible to authenticate

Expiration is time-derived and does not require a scheduled database update.

### Token record

Each issued refresh token has one durable token record.

The record contains at least:

- token-record UUID
- family UUID
- unique SHA-256 digest
- issuance timestamp
- expiration timestamp
- optional consumption timestamp
- optional successor token-record UUID

A token record is active only when:

- its family is active
- its expiration is later than the shared clock time
- it has not been consumed
- it does not identify a successor

A successfully rotated token records both:

- the time at which it was consumed
- the exact successor token-record identifier

Token state is derived from timestamps, successor linkage, and family state. A
mutable free-form status column is not required.

PostgreSQL constraints must prevent:

- duplicate token digests
- a token replacing itself
- a consumed token without a successor
- a successor without a consumption timestamp
- more than one successor for one predecessor
- cross-family predecessor and successor linkage
- token expiration after family expiration
- token issuance before family creation
- unsupported revocation reasons

The persistence issue may introduce triggers or additional constraints when a
plain check constraint or foreign key cannot express an invariant.

## Expiration policy

Refresh-session durations are configuration values.

The implementation defines:

- an individual refresh-token lifetime
- an absolute token-family lifetime

A successor expires at the earlier of:

- its issuance time plus the configured token lifetime
- the family's absolute expiration time

Rotation never extends a session beyond the absolute family lifetime.

All expiration decisions use the shared UTC `Clock`.

Application and persistence tests use fixed or controlled clocks. Refresh
session code does not call `Instant.now()` directly.

## Application boundaries

Refresh-session orchestration remains inside the user module.

The application layer defines focused ports instead of depending directly on:

- Spring Security
- HTTP types
- JDBC or JPA
- secure-random APIs
- cryptographic hashing APIs
- persistence framework entities

Planned responsibilities include ports for:

- access-token generation
- secure refresh-token generation
- refresh-token digest calculation
- refresh-session persistence
- atomic token consumption or row locking
- shared clock access

The existing `TokenGenerationPort` represents access-token generation.
Implementation may rename it to `AccessTokenGenerationPort` before introducing
refresh-token generation so the two credential types cannot be confused.

Plaintext refresh-token material may exist transiently in application memory
only long enough to:

- calculate its digest
- persist the digest and lifecycle metadata
- return the plaintext token to the successful caller

Persistence adapters receive only digests and safe lifecycle metadata.

## Login behavior

The existing credential validation order remains:

1. normalize and resolve the email address
2. reject an unknown account with the existing invalid-credentials outcome
3. verify the password
4. reject an incorrect password with the same invalid-credentials outcome
5. verify that the account is active

After successful validation, login:

1. creates a new refresh-token family
2. generates an initial opaque refresh token
3. persists its digest and initial token record
4. generates a short-lived access token
5. returns the access and refresh credential pair

The response eventually contains at least:

- access token
- access-token type
- access-token expiration
- refresh token
- refresh-token expiration

Family and initial-token persistence occur in one database transaction.

If access-token generation or refresh-session persistence fails, login does not
return a partial credential pair.

The current login use case is read-only. Its implementation will move to the
smallest required write transaction when refresh-session persistence is added.

## Refresh behavior

PayFlow will introduce:

```text
POST /api/v1/auth/refresh
```

The route is public at the Spring Security layer because the refresh credential
authorizes the operation.

The refresh application flow:

1. validates the request shape
2. strictly decodes and hashes the presented token
3. starts one database transaction
4. locates and locks the matching token record and family
5. evaluates token, family, user, and expiration state
6. consumes the active predecessor token
7. creates exactly one successor token record
8. generates a new short-lived access token
9. commits the complete rotation
10. returns the new credential pair

Predecessor consumption and successor creation are atomic.

If access-token generation fails before commit, the transaction rolls back. The
predecessor remains active and no successor remains persisted.

## Concurrent refresh requests

Rotation uses strict single-use semantics.

The persistence implementation serializes competing requests for the same token
through a PostgreSQL row lock or an equivalent atomic conditional update.

For two concurrent requests presenting the same active refresh token:

1. one request consumes the token and creates the successor
2. the other request observes that the predecessor is already consumed
3. the second observation is classified as token reuse
4. the complete family is revoked

The successor returned by the first request may therefore become unusable after
the second request confirms reuse.

This fail-closed result is intentional.

PayFlow does not introduce a concurrent-refresh grace period in v0.7.0.
Clients must serialize refresh operations.

## Reuse detection

Presenting a previously consumed token is evidence that more than one party may
possess credentials belonging to the family.

When reuse is confirmed, PayFlow atomically:

- revokes the complete family
- records `REUSE_DETECTED` as the internal revocation reason
- denies issuance of another credential pair
- increments a safe aggregate security metric
- emits a safe structured security event

The external response does not reveal whether the presented token was:

- unknown
- expired
- revoked
- already consumed
- associated with a family revoked for reuse

These cases share one safe refresh rejection contract.

Raw tokens, token digests, access tokens, passwords, email addresses, and
exception details are excluded from metrics and security events.

## Current-session logout

PayFlow will introduce:

```text
POST /api/v1/auth/logout
```

Current-session logout is authorized by possession of a refresh token.

For a syntactically valid request, the use case:

1. hashes the presented token
2. locates its family when one exists
3. revokes the complete family with `CURRENT_SESSION_LOGOUT`
4. returns HTTP 204

Logout is idempotent.

A syntactically valid token that is unknown, expired, consumed, or already
revoked also returns HTTP 204. The response does not act as a token-existence
oracle.

Normal JSON and field validation failures continue to use the HTTP 400
validation contract.

Historical token digests remain available for reuse detection and may identify
the family when an older rotated token is presented to logout.

## All-session logout

PayFlow will introduce:

```text
POST /api/v1/auth/logout-all
```

This endpoint requires a valid access token.

The authenticated user UUID comes from the verified JWT `sub` claim. The
request does not accept a user ID, email address, or family ID.

The application operation revokes every active family owned by the user with
`ALL_SESSIONS_LOGOUT`.

The endpoint returns HTTP 204 and is idempotent when no active family exists.

Already issued access tokens remain valid until normal expiration.

## User-account state

Possession of a refresh token does not bypass user-account status.

Before issuing a rotated credential pair, PayFlow verifies that the owning user
remains active.

When the account is unavailable:

- no successor is created
- the family is revoked with `USER_ACCOUNT_UNAVAILABLE`
- the safe account-unavailable contract may be returned
- no internal account, family, or token state is exposed

Login retains its existing invalid-credentials and account-unavailable
behavior.

## Public error contracts

Refresh-session APIs use the existing `ApiError` response structure.

Planned stable outcomes are:

| Situation | HTTP status | Safe code |
|---|---:|---|
| Invalid request body | 400 | `VALIDATION_FAILED` |
| Unknown, expired, revoked, or reused refresh token | 401 | `REFRESH_TOKEN_INVALID` |
| User account unavailable | 403 | `USER_ACCOUNT_UNAVAILABLE` |
| Unexpected refresh-session infrastructure failure | 503 | `REFRESH_SESSION_UNAVAILABLE` |

Reuse is classified internally but is not exposed through a distinct public
error code.

Current-session and all-session logout return HTTP 204 for successful and
already-completed revocation outcomes.

Responses never include SQL text, database details, token state, token digests,
exception messages, or stack traces.

## Transaction boundaries

The following operations use independent and explicit PostgreSQL transactions:

- login family and initial-token creation
- refresh predecessor consumption and successor creation
- reuse detection and family revocation
- current-session family revocation
- all-session revocation for one authenticated user

No refresh-session transaction includes:

- Redis
- Kafka
- email delivery
- another remote system

PostgreSQL commit success is required before login or refresh returns newly
generated refresh credentials.

## Trust boundaries

### Client boundary

The client receives and submits plaintext refresh tokens.

PayFlow cannot guarantee how client software stores credentials. Storage
guidance will be documented in OpenAPI, Postman, and release documentation.

### HTTP boundary

HTTP adapters:

- validate request shapes
- do not log credential-bearing request bodies
- derive trusted user identity from verified JWTs where required
- construct application commands
- map application outcomes to fixed public contracts

Controllers do not implement hashing, row locking, rotation, or revocation
rules.

### Application boundary

Application services own refresh-session orchestration and lifecycle decisions.

They depend on application ports and domain models rather than infrastructure
types.

### PostgreSQL boundary

PostgreSQL stores only:

- token digests
- family and token-record identifiers
- user identifiers
- lifecycle timestamps
- successor linkage
- fixed revocation reasons

It is the durable source of truth for rotation and revocation.

### Redis boundary

Redis is reserved for bounded login-attempt protection in a later v0.7.0
increment.

Redis loss, restart, eviction, or unavailability must not:

- reactivate a consumed token
- lose a family revocation
- permit a second successor
- disable reuse detection
- change durable refresh-session correctness

## Logging, metrics, and tracing

The following are forbidden in logs, metrics, traces, audit rows, and
administrative responses:

- plaintext refresh tokens
- refresh-token digests
- access tokens
- authorization headers
- passwords
- credential-bearing request bodies

Safe structured diagnostics may contain:

- fixed event type
- fixed outcome or rejection category
- family UUID
- token-record UUID
- user UUID when required for trusted diagnostics
- request correlation identifier

Email addresses are not included.

Metrics use low-cardinality labels only. Permitted labels include fixed values
such as:

- operation
- outcome
- rejection category

User, family, token-record, digest, email, and correlation identifiers must not
be metric labels.

## Threat model

### Assets

Protected assets include:

- passwords
- plaintext refresh tokens
- access tokens
- refresh-token digests
- refresh-session lifecycle state
- user and family identifiers
- the ability to issue new access tokens
- the ability to revoke active sessions

### Trust boundaries

Relevant trust boundaries are:

- client to HTTP API
- HTTP adapter to application core
- application core to PostgreSQL
- JWT verification to protected endpoints
- application process to logs, metrics, and traces

### Threats and mitigations

| Threat | Mitigation | Residual risk |
|---|---|---|
| Database disclosure | Store only digests of 256-bit random tokens | Family and user metadata remain visible |
| Refresh-token theft | Short token lifetime, absolute family lifetime, single-use rotation | A stolen active token may be used first |
| Replay after rotation | Persist consumption and revoke the family on reuse | Legitimate concurrent refresh may revoke the family |
| Concurrent refresh | PostgreSQL serialization and one-successor constraints | A returned successor may later be revoked |
| Token guessing | At least 256 bits of secure randomness and strict decoding | Denial-of-service attempts remain possible |
| Telemetry leakage | Explicit field denylist and safe allowlists | External infrastructure must also protect bodies |
| Credential role confusion | Separate types, ports, routes, and tests | Implementation defects remain possible |
| Incomplete revocation | Durable family-level revocation | Existing access JWTs remain valid until expiry |
| Account enumeration | Generic login and refresh rejection contracts | Account-unavailable login remains distinct |
| Clock inconsistency | Shared injected UTC clock | Host clock accuracy remains operationally important |
| Redis loss | Exclude Redis from session correctness | Login protection may degrade separately |
| Exception disclosure | Fixed safe errors without exception text | Internal diagnostic access still requires protection |

## Security invariants

The implementation preserves all of the following:

1. No plaintext refresh token is persisted.
2. No token digest is returned through an API.
3. One token record belongs to exactly one family.
4. One active token creates at most one successor.
5. A consumed token never becomes active again.
6. A revoked family never issues another successor.
7. Every successor expires no later than its family.
8. Reuse of a consumed token revokes the complete family.
9. Logout is idempotent.
10. All-session logout derives identity from a verified JWT subject.
11. Redis is not required to evaluate durable token validity.
12. Public responses do not reveal internal token state.
13. Metrics contain no user or credential identifiers.
14. Lifecycle rules remain independent from HTTP and persistence frameworks.

## Consequences

### Positive

- access tokens can remain short-lived
- refresh credentials can be revoked durably
- database disclosure does not reveal plaintext refresh tokens
- single-use rotation limits repeated use of a stolen token
- reuse detection provides a family-wide response boundary
- current-session and all-session logout become possible
- PostgreSQL constraints reinforce application invariants
- concurrency behavior is explicit and testable
- Redis failure cannot corrupt session correctness
- application boundaries remain aligned with the modular monolith

### Negative

- login changes from a read-only operation to a write transaction
- every login creates durable session state
- successful refresh operations create additional writes
- historical token records require a future retention strategy
- strict concurrent-refresh handling can revoke a legitimate session
- logout does not immediately invalidate access JWTs
- credential-bearing request and response DTOs require special care
- schema constraints and concurrency tests increase complexity
- PostgreSQL availability becomes required for login and refresh issuance

## Alternatives considered

### Use long-lived access tokens

Rejected because stolen bearer tokens would remain useful longer and could not
be revoked through the planned session model.

### Use JWT refresh tokens

Rejected because self-contained refresh credentials make single-use rotation,
durable family revocation, and reuse detection harder to enforce.

### Store plaintext refresh tokens

Rejected because database disclosure would reveal immediately usable
credentials.

### Store refresh tokens with BCrypt or Argon2

Rejected because salted password hashes are not suitable for unique indexed
token lookup.

Machine-generated 256-bit tokens already resist offline guessing when stored as
deterministic SHA-256 digests.

### Use Redis as the refresh-session source of truth

Rejected because eviction, restart, memory pressure, and degraded availability
must not lose rotation or revocation facts.

### Add an access-token denylist

Rejected for v0.7.0 because it would add online state to every protected API
request and significantly change the stateless resource-server design.

### Allow a concurrent-refresh grace window

Rejected because reusing a successor would require retaining plaintext
successor material, while creating another successor would weaken single-use
rotation.

### Rotate without token families

Rejected because descendants of a compromised token could not be revoked as one
security boundary.

### Delete consumed token records immediately

Rejected because consumed records are required to detect replay of older
tokens.

### Return different errors for expired, revoked, and reused tokens

Rejected because detailed errors would expose internal token state.

### Accept user or family identifiers in logout-all requests

Rejected because authorization identity must come from the verified JWT
subject, not request-controlled input.

## Verification

Implementation is verified through:

- refresh-session domain invariant tests
- secure-token generation and digest tests
- tests proving at least 256 bits of token entropy
- token-shape and strict-decoding tests
- login orchestration tests
- refresh rotation tests
- PostgreSQL mapping and constraint tests
- row-lock or atomic-consumption integration tests
- controlled concurrent-refresh tests
- reuse-detection and family-revocation tests
- current-session logout tests
- all-session logout tests
- account-unavailable refresh tests
- MockMvc validation and safe-error tests
- tests proving tokens and digests are absent from logs and responses
- tests proving metric labels remain low-cardinality and safe
- OpenAPI JSON contract tests
- Postman authentication-workflow checks
- complete Maven clean verification

The persistence work uses a new additive Flyway migration after `V13`.
Released migrations are not edited.

## Ordered implementation follow-ups

This decision is implemented through separate reviewable issues:

1. define refresh-session domain and application contracts
2. add PostgreSQL family and token-record persistence
3. add secure token-generation and digest adapters
4. extend login to issue access and refresh credentials
5. add atomic refresh rotation
6. add reuse detection and current-session logout
7. add all-session logout
8. add safe metrics and diagnostics
9. update OpenAPI, Postman, architecture, and release documentation
10. complete end-to-end, concurrency, migration, and regression verification

## Out of scope

This ADR does not introduce:

- external OAuth or OpenID Connect providers
- social login
- multi-factor authentication
- password reset
- email verification
- browser-cookie transport
- device fingerprinting
- user-visible session listing
- trusted-device management
- access-token introspection
- access-token denylisting
- production KMS or HSM integration
- refresh-session retention jobs
- administrative session-management APIs
- Redis-backed login-rate-limiting implementation
