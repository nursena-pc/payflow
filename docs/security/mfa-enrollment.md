# TOTP Enrollment and Secret-Protection Contract

- Status: Implemented v0.14.0 increment 2
- Baseline: PayFlow `0.14.0`
- Migration: `V18__create_mfa_authenticators.sql`

## Enrollment profile

PayFlow uses a 160-bit random TOTP secret encoded as canonical unpadded Base32.
Provisioning URIs use the `otpauth://totp` scheme with issuer `PayFlow` by
default, HMAC-SHA1, six digits, and a 30-second period. Confirmation accepts
only the current counter plus one adjacent counter on either side.

Starting enrollment requires bearer authentication, an active account, verified
email ownership, and the current password. A second start while a pending or
enabled authenticator exists returns `MFA_STATE_CONFLICT`; it does not silently
replace an authenticator. Pending enrollment expires after ten minutes by
default and may be explicitly cancelled.

## Secret protection

The plaintext secret exists only while creating the enrollment response or
verifying a TOTP value. PostgreSQL stores only AES-256-GCM-protected bytes. The
encoded format contains a version byte, a 96-bit random nonce, and authenticated
ciphertext. Authenticated data binds the ciphertext to the owning user ID and
the `payflow-mfa-totp-v1` format.

Local development may use an ephemeral process-local key. The production
profile requires `MFA_SECRET_PROTECTION_MODE=configured` and a Base64 value in
`MFA_SECRET_ENCRYPTION_KEY` that decodes to exactly 32 bytes. This key must be
separate from JWT signing keys and `MAIL_CONTENT_ENCRYPTION_KEY`.

## Persistence and concurrency

`mfa_authenticators.user_id` is the primary key, so one user can have at most
one effective pending or enabled authenticator row. Enrollment locks the user
row before checking the authenticator row; confirmation and cancellation use
pessimistic authenticator locking. The database constrains lifecycle timestamps
and rejects protected values shorter than the version + nonce + GCM tag +
160-bit-secret minimum representation.

Absence of an authenticator row represents `DISABLED`. A row may be `PENDING`
or `ENABLED`. Future disable and authenticator-replacement work will extend the
persistence lifecycle only together with its session-revocation semantics.

## Public API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/users/me/mfa` | Return lifecycle metadata only |
| `POST` | `/api/v1/users/me/mfa/enrollment` | Start pending enrollment and return provisioning material once |
| `POST` | `/api/v1/users/me/mfa/enrollment/confirm` | Activate after a valid TOTP proof |
| `DELETE` | `/api/v1/users/me/mfa/enrollment` | Cancel pending enrollment |

The API never returns protected bytes, encryption keys, TOTP values, or a secret
after the original enrollment response. Invalid proofs use
`MFA_VERIFICATION_FAILED`; lifecycle conflicts use `MFA_STATE_CONFLICT`;
protection failures use `MFA_SECURITY_UNAVAILABLE`.

## Explicit non-goals of increment 2

This increment does not change login credential issuance, create MFA login
challenges, generate recovery codes, disable enabled MFA, replace an active
authenticator, create step-up grants, or add generalized abuse protection.
Those remain later v0.14.0/v0.15.0 increments.
