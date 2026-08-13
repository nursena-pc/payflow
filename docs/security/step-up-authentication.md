# Step-Up Authentication Security Contract

## Scope

This release implements the reusable application policy used by account-security
operations to require recent second-factor possession. MFA disable and
recovery-code rotation consume exact purpose-bound grants. Authenticator
replacement and Kafka dead-letter replay/discard step-up enforcement remain
deferred.

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

## Protected and deferred operations

The following public account-security operations require exact step-up purposes:

- `mfa-disable` for MFA disable;
- `recovery-code-rotation` for complete recovery-code replacement.

Both integrations consume the grant inside the same transaction as the protected
mutation. Missing grants use `403 STEP_UP_REQUIRED`; invalid or unusable grants
use `403 STEP_UP_INVALID`.

The `mfa-authenticator-replacement` purpose remains reserved, but active
authenticator replacement is deferred until a safe two-stage lifecycle is
designed and verified.

Kafka dead-letter replay and discard remain bearer-plus-operator-authority
operations. Their typed purposes remain explicit future policy candidates; this
release does not silently change the operations API.

Generalized API-wide abuse protection remains a v0.15.0 concern. Step-up grants
are already short-lived, single-use, purpose-bound, subject-bound, and
superseding.