# MFA Recovery Codes Security Contract

## Scope

Recovery codes provide a one-time fallback proof for users whose TOTP
authenticator is already enabled. This increment covers code generation at MFA
activation, digest-only PostgreSQL persistence, login-challenge consumption,
replay resistance, concurrency, and observable-output boundaries.

Explicit recovery-code rotation is not implemented here. The purpose-bound step-up capability
is now available as a separate application policy, while rotation, MFA disable,
and authenticator replacement remain in the following mutation increment before
public mutation endpoints are added.

## Generation and one-time disclosure

Successful TOTP enrollment confirmation generates exactly ten independent
recovery codes. Each code contains 128 bits of `SecureRandom` entropy encoded as
canonical unpadded Base64URL text, producing 22 characters from the alphabet
`A-Z`, `a-z`, `0-9`, `_`, and `-`.

The enrollment-confirmation response returns the plaintext set once. No
plaintext recovery code is persisted. If a response is lost, the existing TOTP
authenticator remains usable; replacement code issuance is reserved for the
future step-up-protected rotation flow.

## Digest-only persistence

PostgreSQL V20 stores one row per recovery code with:

- a random UUID primary key;
- the owning user identifier;
- a 32-byte SHA-256 digest;
- creation time;
- nullable consumption time.

The database rejects non-32-byte digests, globally duplicated digests, and
consumption timestamps before creation. It contains no plaintext recovery-code
column. An index scopes unconsumed-code lookup by user without changing the
fixed-length digest security boundary.

## Login verification

The existing `POST /api/v1/auth/mfa/challenges/confirm` request retains one
`code` field. A six-digit value follows the TOTP path. A canonical 22-character
Base64URL value follows the recovery-code path. Other shapes fail through the
same public contract.

For recovery-code verification, PayFlow:

1. resolves and locks the owning user, challenge, and enabled authenticator;
2. hashes the supplied recovery code with SHA-256;
3. pessimistically locks the matching code for that user;
4. rejects missing or already consumed rows without revealing their state;
5. consumes the code and challenge in the credential-issuance transaction.

The public response does not reveal whether a proof was a TOTP, recovery code,
unknown value, malformed value, or consumed value. Failure remains
`401 MFA_CHALLENGE_INVALID` and consumes one challenge attempt.

## Concurrency and rollback

A recovery code can be consumed at most once. Concurrent confirmation requests
cannot both receive authenticated sessions because challenge and recovery-code
rows are pessimistically locked. Recovery-code consumption, challenge
consumption, refresh-family creation, and initial refresh-token creation share
one transaction. A downstream credential-issuance failure rolls back the
recovery-code consumption rather than stranding the user with a silently lost
code.

## Observable-output boundary

Recovery-code plaintext and digests must not appear in logs, metric labels,
traces, audit payloads, exception messages, or `toString()` output. The only
intentional plaintext boundary is the successful enrollment-confirmation
response that creates the initial code set and, in a later increment, the
step-up-protected explicit rotation response.

## Deferred work

This increment does not add:

- recovery-code rotation;
- MFA disable;
- authenticator replacement;
- step-up enforcement on recovery-code rotation, MFA disable, or authenticator replacement;
- recovery-code remaining-count disclosure;
- generalized API-wide abuse protection.
