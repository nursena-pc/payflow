# ADR 0014: Keep MFA lifecycle and step-up policy inside the identity domain

- Status: Accepted
- Date: 2026-08-07
- Decision owners: PayFlow maintainers

## Context

PayFlow v0.14.0 adds TOTP multi-factor authentication, single-use recovery
codes, and purpose-bound step-up authentication. The existing identity module
already separates password hashing, JWT issuance, refresh sessions, login
protection, email verification, and password recovery through domain and
application boundaries.

MFA adds new security state that could easily become coupled to Spring Security,
controller branches, JPA entities, or raw cryptographic representations. That
would make lifecycle rules difficult to test, allow transport details to define
security policy, and make future authenticator replacement or alternate factor
support unnecessarily invasive.

The implementation also needs to distinguish account-security session
revocation from password recovery and ordinary logout. Step-up authorization
must not become a collection of ad hoc controller annotations because the same
proof semantics will protect user operations and selected operator commands.

## Decision

### Domain lifecycle

PayFlow freezes an MFA lifecycle with exactly three states:

- `DISABLED`
- `PENDING`
- `ENABLED`

The initial domain transition model permits only:

- `DISABLED -> PENDING` to begin enrollment
- `PENDING -> ENABLED` after successful enrollment proof
- `PENDING -> DISABLED` to cancel incomplete enrollment
- `ENABLED -> DISABLED` after the required authenticated security proof

The lifecycle object contains no Spring, servlet, JWT, JPA, database, or
cryptography type. JPA entities do not enter the MFA domain model. A dedicated
domain exception exposes only the stable message `MFA state transition is
invalid.` for invalid internal transitions.

Persistence and secret material are deliberately absent from this first
aggregate. Later increments will compose the lifecycle with a protected
TOTP-secret record and PostgreSQL locking without changing the allowed state
machine.

### MFA remains independent from account eligibility

`UserStatus`, email-verification state, and MFA lifecycle remain separate
concepts.

Account eligibility answers whether the account may authenticate. MFA answers
whether a second factor is required after password verification. A database or
application shortcut that encodes MFA state into `UserStatus` is rejected.

### Secret-protection boundary

The TOTP secret is sensitive reversible material, unlike password hashes,
refresh-token digests, or recovery-code digests. The implementation will use an
application-facing MFA secret-protection port. The adapter owns reversible
protection and production key loading.

The plaintext secret may exist transiently while generating a provisioning
response or verifying a TOTP value, but it is protected before durable
persistence and excluded from observable output.

The MFA protection key is distinct from JWT signing keys and mail-content
protection keys. Sharing those keys would couple unrelated compromise and
rotation domains.

### Step-up purposes are typed security policy

Step-up authorization uses a closed purpose vocabulary rather than free-form
controller strings. The initial purposes are:

- `mfa-disable`
- `recovery-code-rotation`
- `mfa-authenticator-replacement`
- `kafka-dead-letter-replay`
- `kafka-dead-letter-discard`

Each purpose is associated with either an authenticated user or a PayFlow
operator. The Kafka dead-letter purposes are frozen as operator step-up
candidates, but existing replay and discard behavior is not changed by this
foundation increment.

Future grants must be bound to one authenticated subject and one exact purpose;
a grant created for one purpose cannot satisfy another.

### Account-security refresh revocation

Two additional durable refresh-family reasons are reserved before the mutation
use cases are implemented:

- `MFA_DISABLED`
- `MFA_AUTHENTICATOR_REPLACED`

These reasons make later session invalidation auditable without overloading
`ALL_SESSIONS_LOGOUT`, `PASSWORD_RECOVERY`, or `ADMINISTRATIVE_REVOCATION`.
They are contract names in this foundation increment only. The domain enum and
PostgreSQL revocation-reason constraint must be extended together in the first
increment that actually persists either reason; this pull request does not
create an unusable persistence value ahead of that migration.

The change does not modify access-token residual validity and does not add a JWT
denylist.

### Public failure semantics

The detailed threat model freezes coarse public error names before endpoints
are added:

- `MFA_STATE_CONFLICT`
- `MFA_VERIFICATION_FAILED`
- `STEP_UP_REQUIRED`
- `STEP_UP_INVALID`
- `MFA_SECURITY_UNAVAILABLE`

Internal states such as expired, exhausted, replayed, consumed, superseded,
wrong recovery-code state, or invalid TOTP are not exposed as separate public
login-verification errors.

## Consequences

### Positive

- MFA state transitions are executable domain rules rather than controller
  branches.
- The user aggregate does not gain unrelated authenticator-secret fields.
- JPA, Spring Security, and cryptographic adapters remain replaceable.
- Future recovery-code and step-up work starts from stable typed purposes.
- Session revocation caused by MFA changes has explicit durable reasons.
- The threat model can be tested as a versioned repository contract before
  security-sensitive endpoints exist.

### Trade-offs

- More small domain types are introduced before persistence exists.
- The first increment deliberately provides no endpoint or user-visible MFA
  capability.
- Operator step-up purposes exist before dead-letter command enforcement is
  wired, so documentation must distinguish frozen policy from implemented
  runtime enforcement.

## Rejected alternatives

### Store MFA state on `UserStatus`

Rejected because account availability and second-factor enrollment are
independent lifecycle dimensions. Combining them would create invalid states
and migration risk.

### Let controllers own `PENDING` and `ENABLED` branches

Rejected because transport code would become the security policy and concurrent
persistence operations could bypass the intended state machine.

### Put JPA entities in the domain model

Rejected because database representation, row locking, and protected-secret
columns belong to the persistence adapter.

### Reuse the mail-content protection key

Rejected because MFA authenticator secrets and provider-ready mail payloads
have different compromise, retention, and rotation boundaries.

### Use one generic `SECURITY_CHANGE` refresh revocation reason

Rejected because disable and authenticator replacement are materially distinct
security events and should remain distinguishable without inspecting sensitive
audit payloads.

### Protect operator actions with ad hoc controller annotations

Rejected because step-up proof must be subject-bound, purpose-bound, time-bound,
and reusable across inbound adapters without making controllers authoritative
for security policy.
