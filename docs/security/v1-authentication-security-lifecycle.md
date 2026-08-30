# v1.0.0 Authentication and Security Lifecycle Closure

- Status: Active v1.0.0 release-candidate security contract
- Tracking issue: #192
- Baseline: `ffa8819beede102c995a7530c1b15b0aa4a2fca1`
- Project version: `1.0.0`

## Purpose

This document records the current authentication and account-security behavior
that PayFlow carries into the v1.0.0 release candidate. It closes documentation
drift across the historical v0.13.0 account-action, v0.14.0 MFA/step-up, and
v0.15.0 abuse-protection increments without redefining those historical release
records.

CP2 verification found no runtime release blocker. The five focused lifecycle
groups covering authentication/account actions, JWT/refresh sessions,
MFA/step-up, abuse protection/login limiting, and security/documentation
contracts all passed before this documentation-only closure candidate.

## Current lifecycle

### Email ownership verification

Registration creates the initial verification credential in the user
transaction. Verification credentials are high-entropy opaque values persisted
only through digests. Verification re-request behavior remains generic and does
not disclose account existence or eligibility. Confirmation consumes one valid
credential exactly once and records verified ownership.

### Password recovery

Password-recovery requests retain the generic accepted-response boundary.
Confirmation uses the existing password policy, replaces the BCrypt password
hash, consumes the recovery credential, and revokes active refresh-token
families atomically with the `PASSWORD_RECOVERY` reason.

### Password login, JWT, and refresh sessions

Password validation precedes account-eligibility disclosure. Access tokens are
RSA-signed RS256 JWTs with a stable `kid`; production uses configured signing
material and verification accepts only the configured active or immediately
previous public key according to the existing rotation contract.

Opaque refresh credentials are stored only as SHA-256 digests. Refresh rotation,
reuse detection, current-session revocation, all-session revocation, and
account-security revocation semantics remain unchanged.

### MFA enrollment and login

Starting MFA enrollment requires bearer authentication for the owning subject,
an active account, verified email ownership, and the current password. The
controller passes `request.currentPassword()` into the enrollment command.

A pending TOTP secret is protected with AES-256-GCM before PostgreSQL
persistence. The plaintext secret and provisioning URI cross only the response
that creates the pending enrollment. Confirmation requires a valid TOTP proof.

When MFA is enabled, successful password verification does not issue access or
refresh credentials. It creates only the existing short-lived opaque MFA login
challenge. A valid TOTP or one unused recovery code must consume that challenge
successfully before authenticated credentials are issued.

Recovery codes are returned only at activation or explicit rotation and are
persisted only through digests.

### Step-up protected account-security operations

Step-up grants remain short-lived, subject-bound, exact-purpose-bound,
single-use, superseding opaque credentials persisted only through digests.

The implemented account-security integrations are:

- `mfa-disable`
- `recovery-code-rotation`

MFA disable and recovery-code rotation require their exact purpose-bound grant.
Successful MFA disable revokes active refresh-token families with the existing
account-security reason.

The `mfa-authenticator-replacement` purpose remains reserved; active
authenticator replacement remains deferred until a separately reviewed
two-stage lifecycle is designed and verified.

Kafka dead-letter replay/discard purposes remain explicit future enforcement
candidates. Their existing operator authorization behavior is not changed by
the v1.0.0 release-candidate security closure.

### Abuse protection and password-login limiting

Generalized abuse protection remains wired for:

- email-verification requests
- password-recovery requests
- MFA login-challenge confirmation
- step-up grant issuance

Registration remains evidence-backed `DEFER`; configuration presence does not
mean registration enforcement is active.

Password login retains its existing separate Redis-backed fixed-window limiter.
CP2 does not retune its thresholds, keys, public responses, or dependency
behavior.

Wired generalized workflows retain their documented fail-closed dependency
semantics. Verification and recovery preserve generic accepted responses.
MFA/step-up dependency failures preserve their existing coarse security
contracts.

## Trust, privacy, and observable-output boundaries

Current security behavior continues to require:

- trusted-proxy-aware effective client resolution before client-dimension policy
  decisions;
- no passwords, TOTP values, recovery codes, raw MFA challenges, step-up grants,
  digests, encryption keys, normalized email addresses, or raw client addresses
  in logs, metrics, traces, audit payloads, or committed evidence;
- bounded public errors that do not disclose account existence, recovery-code
  state, challenge state, quota state, or credential validity detail;
- PostgreSQL serialization/locking for security-sensitive single-winner
  transitions where the existing implementation requires it;
- injected UTC clock boundaries for security time decisions;
- fail-fast production configuration for required cryptographic material.

## Historical evidence boundary

The following documents remain versioned historical evidence and are not
rewritten as if their increment-scoped non-goals described the entire v1 system:

- `docs/security/mfa-enrollment.md`
- `docs/security/mfa-login-challenge.md`
- `docs/security/mfa-recovery-codes.md`
- `docs/security/step-up-authentication.md`
- `docs/security/abuse-protection-threat-model.md`
- the v0.13.0, v0.14.0, and v0.15.0 release records and ADRs

The historical MFA threat model remains the foundation invariant baseline, while
this document records current release-candidate implementation status.

## Explicit v1.0.0 security non-goals

CP2 does not add or activate:

- registration abuse-protection enforcement;
- new password-login limiter semantics or thresholds;
- active-authenticator replacement;
- WebAuthn, passkeys, FIDO2, SMS, voice, or email OTP authentication;
- OAuth, OpenID Connect, SAML, or social login;
- trusted-device or remember-device behavior;
- access-token denylisting or online JWT introspection;
- adaptive risk scoring, device fingerprinting, geolocation, or CAPTCHA;
- new Kafka step-up enforcement;
- a new cryptographic provider, KMS, HSM, Vault, or microservice extraction.

Any future activation or authentication-scope expansion requires separate
evidence, implementation, review, and versioned security documentation.
