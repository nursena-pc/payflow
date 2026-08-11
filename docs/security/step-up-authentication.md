# Step-Up Authentication Security Contract

## Scope

This increment implements the reusable application policy that later v0.14.0
account-security and selected operator operations use to require recent
second-factor possession. It does not yet disable MFA, rotate recovery codes,
replace an authenticator, or change Kafka dead-letter replay/discard runtime
authorization.

Spring Security still establishes the authenticated bearer subject. Controllers
may pass that subject into a use case, but controller annotations do not decide
whether a step-up grant authorizes a security-sensitive operation.

## Grant issuance

`POST /api/v1/users/me/step-up/grants` requires bearer authentication, an exact
closed `StepUpPurpose`, and a valid second-factor proof. The proof may be the
same six-digit TOTP accepted by MFA login or one currently unused recovery code.
A recovery code consumed while issuing a grant participates in the same
transaction as grant persistence, so a failed grant write rolls the recovery
code consumption back.

The frozen purpose vocabulary remains:

- `mfa-disable`
- `recovery-code-rotation`
- `mfa-authenticator-replacement`
- `kafka-dead-letter-replay`
- `kafka-dead-letter-discard`

Operator purposes are issued only to a subject whose persisted PayFlow role is
`ADMIN`. Request-controlled role headers never influence this decision.

## Opaque grant credential

Every successful issuance generates 32 cryptographically secure random bytes
and encodes them as canonical unpadded Base64URL text. The resulting 43-character
plaintext grant crosses only the successful issuance response boundary.
PostgreSQL V21 stores only its 32-byte SHA-256 digest.

The default lifetime is five minutes and configuration rejects non-positive
lifetimes or values longer than fifteen minutes. A new grant supersedes any
older unconsumed grant for the same subject and exact purpose before the new
credential is persisted.

## Application-facing authorization policy

`StepUpAuthorizationPolicy` is independent from servlet, controller, JWT, and
Spring Security annotation types. A caller supplies the already-authenticated
subject, the exact typed purpose required by the operation, and the opaque grant
credential.

The persistence implementation digests attacker-controlled plaintext, locks the
candidate grant, and accepts it only when all of these conditions hold:

- the persisted subject equals the authenticated subject;
- the persisted purpose equals the exact operation purpose;
- the grant has not expired;
- the grant has not been superseded;
- the grant has not already been consumed.

Successful authorization consumes the grant exactly once. Pessimistic locking
ensures concurrent submissions have at most one successful winner.

Unknown, malformed, wrong-subject, wrong-purpose, expired, superseded, and
replayed credentials all fail through the same `403 STEP_UP_INVALID` public
contract when a protected operation uses the policy. Absence of a required
grant uses the separate frozen `403 STEP_UP_REQUIRED` contract.

## Observable-output boundary

The following values must not enter logs, metric labels, traces, audit payloads,
exception messages, or durable plaintext columns:

- plaintext step-up grant;
- step-up grant digest;
- TOTP value;
- plaintext recovery code;
- revealed TOTP secret.

Request, result, response, generated-grant, and digest value objects redact
credential material from `toString()` output.

## Bearer-only and future step-up operations

This increment creates and validates purpose-bound grants but deliberately does
not yet change existing operation behavior. The next account-security increment
will require:

- `mfa-disable` for MFA disable;
- `recovery-code-rotation` for recovery-code replacement;
- `mfa-authenticator-replacement` for active-authenticator replacement.

Kafka dead-letter replay and discard remain bearer-plus-operator-authority
operations in this increment. Their typed purposes are explicit candidates for
a later policy integration; this increment does not silently change the
operations API.

Generalized API-wide abuse protection remains a v0.15.0 concern. Step-up grant
credentials themselves are short-lived, single-use, purpose-bound, subject-bound,
and superseding; the generalized request-rate policy is not introduced here.
