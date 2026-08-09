# MFA Login Challenge Security Contract

## Scope

This increment changes the password-login outcome only for users whose TOTP
MFA authenticator is already `ENABLED`. Password verification remains the first
factor and the existing Redis-backed password-attempt protection still executes
before user lookup and password verification.

A successful password check does not create an access token, refresh-token
family, or refresh-token record for an MFA-enabled user. Instead the application
returns one short-lived opaque challenge. Only successful challenge confirmation
may enter the existing credential-issuance boundary.

Recovery codes, MFA disable, authenticator replacement, and reusable step-up
grants are intentionally outside this increment.

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

`POST /api/v1/auth/mfa/challenges/confirm` accepts the opaque challenge and a
TOTP proof. Verification resolves the digest to a candidate user, locks the user,
locks the challenge, and locks the enabled authenticator before revealing the
protected TOTP secret.

A valid TOTP proof uses the same RFC 4226/6238 profile as enrollment: HMAC-SHA1,
six digits, 30-second steps, and one adjacent counter on either side. The
plaintext secret is zeroed after verification.

Challenge states are:

- `PENDING` — unresolved and within the attempt budget.
- `CONSUMED` — successfully completed exactly once.
- `EXHAUSTED` — the final permitted invalid proof was used.
- `EXPIRED` — the lifetime elapsed before successful confirmation.
- `SUPERSEDED` — a newer successful password stage replaced it.

Invalid TOTP proofs decrement the persisted attempt budget. The transition to
`EXHAUSTED` and the transition to `EXPIRED` are committed even though the public
request returns an authentication failure. A consumed, exhausted, expired,
superseded, unknown, oversized, blank, or replayed challenge uses the same
`401 MFA_CHALLENGE_INVALID` contract as an invalid TOTP proof.

## Concurrency

Challenge confirmation uses pessimistic locking. Two concurrent confirmations
for the same challenge cannot both cross the credential-issuance boundary. The
winner persists `CONSUMED` before access and refresh credentials are created;
the loser observes a terminal challenge and receives the generic unauthorized
contract. Integration verification requires exactly one refresh-token family
and one initial refresh-token record after concurrent replay.

## Observable-output boundary

The following values must not enter logs, metric labels, traces, audit payloads,
exception messages, or persistent plaintext columns:

- challenge plaintext;
- challenge digest;
- TOTP code;
- revealed TOTP secret;
- access token;
- refresh token.

Request, response, command, generated-challenge, and digest value objects use
redacted `toString()` behavior where credentials could otherwise be exposed.

## Operational configuration

```text
MFA_LOGIN_CHALLENGE_TTL=5m
MFA_LOGIN_CHALLENGE_MAX_ATTEMPTS=5
```

These settings bound only the post-password MFA challenge. Generalized API-wide
abuse protection remains a v0.15.0 concern.
