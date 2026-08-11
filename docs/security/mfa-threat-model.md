# MFA and Step-Up Authentication Threat Model

- Status: Active v0.14.0 security contract
- Scope: TOTP enrollment, MFA login completion, recovery codes, MFA disable,
  authenticator replacement, and selected step-up operations
- Baseline: PayFlow `0.14.0-SNAPSHOT`

## Purpose

This document freezes the security boundaries that must exist before PayFlow
persists an MFA authenticator, accepts a TOTP value, issues a recovery code, or
creates a step-up grant. It intentionally precedes endpoint and persistence
implementation so later increments cannot silently redefine the trust model.

The first factor remains the existing password flow. MFA is an additional proof
for eligible accounts; it is not a replacement for password verification,
email-ownership verification, refresh-session rotation, or the existing login
rate limiter.

## Security invariants

The v0.14.0 implementation must preserve all of the following invariants.

1. MFA state is independent from `UserStatus` and `emailVerifiedAt`.
2. Only `ENABLED` MFA requires a second factor during login.
3. `PENDING` enrollment never grants MFA-protected authentication status.
4. An enabled user receives no access or refresh credential until a valid MFA
   login challenge is consumed successfully.
5. TOTP secrets, provisioning URIs, TOTP values, plaintext recovery codes,
   login-challenge credentials, step-up grants, secret-protection keys, and
   protected secret bytes never appear in logs, metrics, traces, audit payloads,
   exception messages, or durable plaintext storage.
6. Recovery codes and opaque challenge credentials are persisted only through
   fixed-length cryptographic digests suitable for equality lookup.
7. Every security-sensitive state transition is serialized at the persistence
   boundary so concurrent requests cannot create two winners.
8. Security time decisions use an injected UTC `Clock`; MFA code does not call
   `Instant.now()` directly.
9. Disabling MFA or replacing the active authenticator revokes active refresh
   families with an explicit account-security reason.
10. Existing access tokens retain the documented short residual-validity
    boundary. v0.14.0 does not introduce access-token denylisting.

## Trust boundaries

### Client to PayFlow

The client may supply passwords, MFA challenge credentials, TOTP values,
recovery codes, and step-up grants. Every supplied value is attacker-controlled
until shape and state validation succeeds.

TLS termination and trusted reverse-proxy resolution remain deployment
responsibilities already defined by the existing security architecture. MFA
must not derive security decisions from untrusted host, forwarding, or client
metadata.

### Application to PostgreSQL

PostgreSQL is the system of record for durable MFA state, challenge state,
recovery-code digests, and digest-only step-up grant state.
Database readers must not obtain a usable TOTP secret or plaintext recovery
credential.

The persistence adapter owns row locking, uniqueness constraints, and database
representations. JPA entities do not enter the MFA domain model.

### Application to secret protection

A future application-facing MFA secret-protection port separates TOTP secret
handling from the domain model. The adapter may use local AES-GCM key material
in the first implementation, but provider-specific cryptography and key-loading
types remain outside the domain and application policies.

Production must fail safely when required protection material is absent or
invalid. The production key must be separate from JWT signing keys and the
mail-content protection key.

### Authentication and authorization adapters

JWT parsing, Spring Security authorities, servlet request handling, and
controller annotations remain adapters around the MFA use cases. They may
supply an authenticated subject, but they do not own MFA lifecycle or step-up
policy.

## MFA lifecycle

The lifecycle has exactly three states:

```text
DISABLED --begin enrollment--> PENDING --valid enrollment proof--> ENABLED
    ^                            |
    |                            +--cancel enrollment---------------+
    |
    +---------------------disable with required proof---------------+
```

Allowed transitions are:

- `DISABLED -> PENDING`: begin enrollment
- `PENDING -> ENABLED`: confirm enrollment with a valid TOTP proof
- `PENDING -> DISABLED`: cancel an incomplete enrollment
- `ENABLED -> DISABLED`: disable MFA after the required recent step-up proof

All other direct transitions are invalid. In particular:

- `DISABLED -> ENABLED` is forbidden
- `ENABLED -> PENDING` is not a replacement shortcut
- repeated enrollment cannot create overlapping pending authenticators
- replacement must be a dedicated serialized use case that preserves one
  effective authenticator outcome

The domain lifecycle is deliberately independent from controllers, JWT
adapters, JPA entities, and secret-protection formats.

## Enrollment threats

### Enrollment hijack

**Threat:** an attacker with a stolen bearer token attempts to enroll their own
authenticator on the victim account.

**Boundary:** enrollment requires an authenticated, active, email-verified
subject. The implementation increment must define whether an additional recent
password or step-up proof is required before activation; silently treating a
long-lived bearer token as sufficient for every lifecycle mutation is not
allowed.

### Secret disclosure

**Threat:** a TOTP secret leaks through database plaintext, logs, exceptions,
metrics, tracing, or repeated API reads.

**Boundary:** the plaintext secret and `otpauth://` provisioning value may be
returned only by the enrollment response that created that pending secret. The
secret is protected before durable persistence and is never returned again.

### Overlapping enrollment

**Threat:** concurrent enrollments create multiple pending or active secrets and
make the effective authenticator ambiguous.

**Boundary:** persistence serializes enrollment state. At most one effective
pending or active authenticator exists for one user. Replacement is explicit
rather than an accidental side effect of a second enrollment request.

### Activation replay

**Threat:** the same activation request succeeds more than once or activates a
superseded pending secret.

**Boundary:** activation verifies the currently effective pending secret under a
lock and performs one `PENDING -> ENABLED` transition. A stale or already-used
request receives the same safe lifecycle failure contract.

## MFA login-challenge threats

### Second-factor bypass

**Threat:** password verification issues access or refresh credentials before
MFA succeeds.

**Boundary:** when MFA is `ENABLED`, successful password and account-eligibility
checks create only a short-lived opaque MFA login challenge. Access and refresh
credentials are issued only after that challenge is consumed successfully.

### Challenge theft

**Threat:** a database reader or operational output exposes a usable login
challenge.

**Boundary:** challenge credentials contain at least 256 bits of secure random
entropy. Only a fixed-length SHA-256 digest is persisted. Raw credentials and
digests are excluded from observable output.

### Brute force

**Threat:** an attacker submits unbounded TOTP or recovery-code guesses against
a valid challenge.

**Boundary:** each challenge has a bounded attempt count and short expiration.
The exact threshold is an implementation configuration, not a reusable general
API rate-limit policy. Generalized abuse protection remains a v0.15.0 concern.

### Replay and concurrent verification

**Threat:** two requests consume one valid challenge or one recovery code and
both receive authenticated sessions.

**Boundary:** challenge and recovery-code consumption are serialized in
PostgreSQL. Exactly one request may perform the terminal success transition and
create the corresponding authenticated session outcome.

### Clock manipulation

**Threat:** broad clock skew makes captured TOTP values valid for too long.

**Boundary:** TOTP verification uses the shared UTC `Clock` and a bounded window.
The implementation target is the current time step plus at most one adjacent
step on either side. The exact TOTP profile is frozen with test vectors in the
TOTP implementation increment.

## Recovery-code threats

### Durable plaintext recovery codes

**Threat:** a database compromise provides reusable recovery credentials.

**Boundary:** plaintext recovery codes are returned once at activation or
explicit rotation. PostgreSQL stores only fixed-length digests. Recovery-code
format and entropy are fixed by the implementation increment and verified with
unit tests.

### Recovery-code enumeration

**Threat:** public responses reveal whether a supplied proof was a TOTP value,
a recovery code, already consumed, or structurally valid.

**Boundary:** login-challenge proof failures use the same public failure
contract. Internal diagnostics may use bounded reason categories but never
include the credential, digest, or remaining-code count.

### Recovery-code replay

**Threat:** one recovery code is accepted twice under concurrent requests.

**Boundary:** the matching digest is locked and consumed atomically with the
challenge outcome. A consumed code is never restored by a failed downstream
session write; the entire authentication transaction commits or rolls back.

## Disable and authenticator-replacement threats

### Stolen bearer token disables MFA

**Threat:** an attacker with only a valid access token weakens the account by
disabling MFA.

**Boundary:** MFA disable requires a recent step-up grant for the same subject
and the exact `mfa-disable` purpose. Bearer authentication alone is
insufficient.

### Existing refresh sessions survive a security change

**Threat:** disabling MFA or replacing the authenticator leaves previously
issued refresh sessions usable indefinitely.

**Boundary:** successful disable revokes active refresh families with
`MFA_DISABLED`. Successful authenticator replacement revokes them with
`MFA_AUTHENTICATOR_REPLACED`.

Already-issued access tokens keep the existing short residual-validity window.

## Step-up threats

### Confused-deputy or cross-purpose reuse

**Threat:** a grant created for one operation authorizes another operation.

**Boundary:** every grant is bound to one authenticated subject, one exact
purpose, one issue time, and one short expiration. A purpose is not a free-form
controller string.

The frozen purpose vocabulary is:

| Stable value | Actor | Intended operation |
| --- | --- | --- |
| `mfa-disable` | authenticated user | Disable enabled MFA |
| `recovery-code-rotation` | authenticated user | Replace recovery-code set |
| `mfa-authenticator-replacement` | authenticated user | Replace active authenticator |
| `kafka-dead-letter-replay` | PayFlow operator | Replay a dead-letter record |
| `kafka-dead-letter-discard` | PayFlow operator | Discard a dead-letter record |

The two Kafka purposes are explicit operator step-up candidates. Their runtime
enforcement is introduced only when the step-up increment integrates with the
existing operations module.

### Grant theft and replay

**Threat:** a captured step-up grant is reused by another subject or after its
first accepted use.

**Boundary:** wrong-subject, wrong-purpose, expired, superseded, and replayed
grants fail through one stable step-up failure contract. Persisted grants, when
introduced, use opaque credential and digest-only rules rather than plaintext
bearer storage.

## Stable public failure contracts

The implementation must use bounded public semantics rather than exposing
internal state. These names are frozen before endpoint implementation:

| HTTP intent | Error code | Public meaning |
| --- | --- | --- |
| `409 Conflict` | `MFA_STATE_CONFLICT` | The authenticated lifecycle operation cannot be applied in the current public state. |
| `401 Unauthorized` | `MFA_VERIFICATION_FAILED` | The MFA login challenge or supplied second-factor proof cannot be accepted. |
| `403 Forbidden` | `STEP_UP_REQUIRED` | The authenticated operation requires a recent purpose-bound second-factor proof. |
| `403 Forbidden` | `STEP_UP_INVALID` | The supplied step-up proof cannot authorize the requested operation. |
| `503 Service Unavailable` | `MFA_SECURITY_UNAVAILABLE` | PayFlow cannot make a safe MFA security decision because required security infrastructure is unavailable. |

`MFA_VERIFICATION_FAILED` intentionally covers malformed, unknown, expired,
exhausted, consumed, replayed, and superseded challenge state plus invalid TOTP
or recovery-code proof. The public response does not disclose which condition
occurred or how many recovery codes remain.

Authenticated lifecycle endpoints may expose coarse `DISABLED`, `PENDING`, or
`ENABLED` state to the owning user when the endpoint contract explicitly needs
it; they never expose secret material or internal challenge state.

## Concurrency requirements

The persistence implementation must prove the following single-winner
properties with real PostgreSQL tests:

- concurrent enrollment starts cannot create two effective pending secrets
- activation and cancellation of the same pending enrollment cannot both win
- replacement cannot leave two active authenticators
- two valid submissions cannot both consume one login challenge
- two requests cannot both consume one recovery code
- disable and recovery-code rotation cannot bypass required step-up proof
- session revocation and account-security mutation commit atomically where the
  use case requires both

In-memory synchronization, JVM-local locks, or optimistic assumptions are not
sufficient evidence because PayFlow may run more than one application process.

## Observable-output policy

MFA security events may contain only bounded categories such as operation,
outcome, factor kind, and stable reason class. They must not contain:

- normalized email address
- TOTP secret or provisioning URI
- TOTP value
- plaintext recovery code or recovery-code digest
- raw challenge or challenge digest
- raw step-up grant or grant digest
- secret-protection key or protected secret bytes
- remaining recovery-code values

Correlation IDs may connect synchronous request logs but never become an MFA
credential, grant identifier, persistence key, or metric label.

## Explicit non-goals

This threat model does not add:

- SMS, voice, or email one-time passwords
- WebAuthn, passkeys, FIDO2, or biometric authentication
- trusted-device or remember-this-device cookies
- external OAuth, OpenID Connect, SAML, or social login
- device fingerprinting, geolocation, or adaptive risk scoring
- generalized API-wide abuse protection
- access-token denylisting or online JWT introspection
- remote KMS, HSM, Vault, Kubernetes, or microservice extraction

Those capabilities require separate versioned threat models rather than being
silently folded into the TOTP MFA increment.
