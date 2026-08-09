# MFA Login Challenge Security Contract

## Scope

Password verification remains the first factor and the existing Redis-backed
password-attempt protection still executes before user lookup and password
verification. A successful password check does not create an access token,
refresh-token family, or refresh-token record for an MFA-enabled user. Instead
the application returns one short-lived opaque challenge.

Challenge confirmation accepts either the enabled authenticator's six-digit
TOTP proof or one unused MFA recovery code. Only successful second-factor
confirmation may enter the existing credential-issuance boundary.

MFA disable, recovery-code rotation, authenticator replacement, and reusable
step-up grants remain outside this increment sequence until purpose-bound
step-up authorization exists.

## Challenge credential

The login challenge is generated from 32 cryptographically secure random bytes
and encoded as canonical unpadded Base64 URL text. The plaintext value crosses
only the password-login response boundary. PostgreSQL stores a 32-byte SHA-256
digest and never stores the plaintext challenge.

The default challenge lifetime is five minutes and the default verification
budget is five attempts. Configuration rejects non-positive lifetimes, lifetimes
longer than fifteen minutes, and attempt budgets outside 1 through 10.

Issuing a new challenge supersedes the user's prior unresolved challenge while
the owning user row is locked. PostgreSQL additionally enforces at most one
`PENDING` challenge per user.

## Verification and terminal states

`POST /api/v1/auth/mfa/challenges/confirm` accepts the opaque challenge and one
second-factor proof. Verification resolves the challenge digest to a candidate
user, locks the user, locks the challenge, and locks the enabled authenticator
before selecting the proof path.

A six-digit proof uses the existing RFC 4226/6238 profile: HMAC-SHA1, six digits,
30-second steps, and one adjacent counter on either side. The revealed plaintext
TOTP secret is zeroed after verification.

A canonical 22-character Base64URL proof is treated as a recovery-code
candidate. PayFlow hashes it with SHA-256, pessimistically locks a matching code
for the challenge user, and accepts only an unconsumed row. The recovery code is
consumed in the same transaction as challenge consumption and credential
issuance.

Challenge states are:

- `PENDING` — unresolved and within the attempt budget.
- `CONSUMED` — successfully completed exactly once.
- `EXHAUSTED` — the final permitted invalid proof was used.
- `EXPIRED` — the lifetime elapsed before successful confirmation.
- `SUPERSEDED` — a newer successful password stage replaced it.

Invalid TOTP, unknown recovery-code, malformed recovery-code, and consumed
recovery-code proofs decrement the same persisted challenge attempt budget.
`EXHAUSTED` and `EXPIRED` transitions are committed even though the public
request returns an authentication failure. Consumed, exhausted, expired,
superseded, unknown, oversized, blank, replayed, and invalid-proof outcomes use
the same `401 MFA_CHALLENGE_INVALID` contract.

## Concurrency and transaction boundary

Challenge confirmation uses pessimistic locking. Two concurrent confirmations
for the same challenge cannot both cross the credential-issuance boundary. A
recovery-code row is also pessimistically locked before it is consumed. The
winner persists both recovery-code consumption when applicable and challenge
`CONSUMED` before access and refresh credentials are created; all of those writes
share the same transaction. If downstream credential issuance fails, the
recovery-code and challenge writes roll back together.

Integration verification requires exactly one refresh-token family and one
initial refresh-token record after concurrent replay.

## Observable-output boundary

The following values must not enter logs, metric labels, traces, audit payloads,
exception messages, or persistent plaintext columns:

- challenge plaintext;
- challenge digest;
- TOTP code;
- revealed TOTP secret;
- recovery-code plaintext;
- recovery-code digest;
- access token;
- refresh token.

Request, response, command, generated-credential, and digest value objects use
redacted `toString()` behavior where credentials could otherwise be exposed.

## Operational configuration

```text
MFA_LOGIN_CHALLENGE_TTL=5m
MFA_LOGIN_CHALLENGE_MAX_ATTEMPTS=5
```

These settings bound the post-password MFA challenge regardless of which
second-factor proof is supplied. Generalized API-wide abuse protection remains
a v0.15.0 concern.
