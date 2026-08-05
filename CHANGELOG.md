<!-- payflow-release-v0.8.0 -->
# Changelog

All notable PayFlow changes are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and PayFlow uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Nullable email-verification state independent from account status, with existing users safely backfilled during the V15 migration.
- Package-bounded user-domain behavior for one-time email verification and password replacement through the future recovery workflow.
- Constrained digest-only account-action credential storage for email verification and password recovery.
- Purpose-specific account-action credential issuance and consumption with configurable verification and recovery lifetimes.
- Registration-time email-verification issuance plus generic request and single-use confirmation endpoints.
- Configuration-derived verification links that never trust request host headers.
- Generic password-recovery request and single-use confirmation endpoints.
- V16 refresh-session revocation support for the dedicated `PASSWORD_RECOVERY` reason.
- Dedicated V17 account-action mail outbox with leased PostgreSQL dispatch and SMTP delivery.
- AES-256-GCM content protection with ephemeral local and configured production key modes.
- Mailpit-backed Compose wiring and account-action mail operations guidance.

### Security

- New registrations remain unverified while every pre-v0.13.0 account keeps its existing authentication eligibility.
- PostgreSQL enforces credential purpose, SHA-256 digest length, lifetime, terminal-state consistency, and at most one unresolved credential per user and purpose.
- Account-action credentials use 256 bits of secure randomness, strict unpadded Base64 URL encoding, digest-only persistence, serialized replacement, and single-winner consumption.
- Correct passwords for active but unverified accounts receive the existing stable unavailable-account response without creating a refresh session.
- Unknown, closed, and already-verified identities receive the same accepted verification-request response.
- Password recovery reuses the registration password-strength and BCrypt policy.
- Successful recovery consumes the opaque credential, replaces the hash, and revokes every active refresh-token family atomically.
- Invalid, expired, consumed, and superseded recovery credentials share one stable public error contract.
- Provider-ready verification and recovery links are protected before persistence and erased after terminal delivery outcomes.
- SMTP failures occur outside user transactions and cannot roll back valid registration or password-recovery state.
- Mail logs and retry metadata exclude recipients, links, credentials, digests, and protected content.

## [0.12.0] - 2026-08-04

### Added

- Stable JWT `kid` issuance for the active RSA signing key.
- RS256-only verification with active and previous public-key overlap.
- Adapter-local key-provider boundary for future external KMS or HSM adapters.
- Configured PKCS#8 private-key and X.509 public-key resource loading.
- Fail-fast production validation for missing, malformed, weak, duplicated, or mismatched key material.
- Rotation, rollback, retirement, and emergency-recovery operations guide.

### Security

- Tokens with missing or unknown key identifiers fail authentication.
- The previous key is verification-only and cannot issue new tokens.
- Production never falls back to process-local ephemeral key material.
- Runtime key directories and PEM files remain outside source control and container images.

### Completed milestone work

- [#112](https://github.com/nursena-pc/payflow/issues/112) feat(security): add JWT signing-key rotation
- [#113](https://github.com/nursena-pc/payflow/pull/113) feat(security): add JWT signing-key rotation

## [0.11.0] - 2026-08-03

### Added

- Trustworthy request correlation with strict inbound identifier validation.
- Global `X-Correlation-ID` response and centralized API-error correlation.
- Single-line structured JSON logging for operational profiles.
- Centralized masking for credentials, tokens, secrets, and JWT-like values.
- One bounded request-completion event per synchronous HTTP request.

### Security

- Raw paths, query strings, bodies, headers, financial values, and user identifiers remain outside request-completion events.
- Attacker-controlled correlation input cannot inject log structure.
- Correlation identifiers are excluded from authentication, authorization, business rules, and metric labels.

### Completed milestone work

- [#107](https://github.com/nursena-pc/payflow/issues/107) feat(observability): add structured logging and request correlation
- [#108](https://github.com/nursena-pc/payflow/pull/108) feat(observability): add structured logging and request correlation
- [#111](https://github.com/nursena-pc/payflow/pull/111) chore(release): prepare v0.11.0

## [0.10.0] - 2026-08-01

### Added

- Explicit trusted-proxy CIDR configuration with startup validation.
- Literal-only IPv4 and IPv6 forwarding-chain parsing without DNS resolution.
- Deterministic effective-client selection across trusted proxy hops.
- Spoofing-resistant integration with Redis-backed login protection.
- Bounded source and fallback metrics without raw client-address labels.

### Security

- Forwarding headers from untrusted direct peers are ignored.
- Malformed, obfuscated, oversized, or excessive-hop input fails safely to the direct peer.
- Raw forwarding data and client addresses stay out of logs and metric labels.

### Completed milestone work

- [#101](https://github.com/nursena-pc/payflow/issues/101) feat(security): add trusted reverse-proxy client context
- [#102](https://github.com/nursena-pc/payflow/pull/102) chore(project): start v0.10.0 development
- [#103](https://github.com/nursena-pc/payflow/pull/103) feat(security): add trusted reverse-proxy client context
- [#104](https://github.com/nursena-pc/payflow/pull/104) chore(release): prepare v0.10.0

## [0.9.0] - 2026-07-30

### Added

- Redis-backed fixed-window login protection by normalized identity and effective client.
- Atomic Lua counter updates with explicit expiration.
- Stable `429 LOGIN_RATE_LIMIT_EXCEEDED` responses with positive `Retry-After`.
- Fail-closed `503 LOGIN_RATE_LIMIT_UNAVAILABLE` behavior for unsafe Redis failures.
- Low-cardinality security metrics and credential-free operational events.

### Security

- Redis keys contain SHA-256 digests rather than raw identities or addresses.
- Login responses preserve generic authentication failure semantics.
- Parallel requests cannot bypass the configured identity or client threshold.

### Completed milestone work

- [#98](https://github.com/nursena-pc/payflow/issues/98) feat(auth): add Redis-backed login rate limiting
- [#99](https://github.com/nursena-pc/payflow/pull/99) feat(auth): add Redis-backed login rate limiting
- [#100](https://github.com/nursena-pc/payflow/pull/100) chore(release): prepare v0.9.0

## [0.8.0] - 2026-07-29

### Added

- Durable opaque refresh-token sessions backed by PostgreSQL.
- SHA-256 digest-only refresh credential persistence and secure token generation.
- Refresh credentials issued during successful login.
- Single-use refresh-token rotation with access and refresh credential renewal.
- Refresh-token family revocation when consumed-token reuse is detected.
- Public `POST /api/v1/auth/logout` for current-session family revocation.
- OpenAPI contracts and security configuration for refresh and logout operations.
- PostgreSQL integration coverage for persistence, locking, rollback, and concurrency.

### Security

- Raw refresh credentials and stored digests remain redacted from public representations.
- Refresh rotation serializes on pessimistic record and family locks.
- Reuse detection invalidates every credential in the affected family.
- Logout returns a state-independent `204 No Content` response for validly shaped credentials.
- Logout-versus-rotation races preserve one-successor and first-revocation-reason guarantees.
- Persistence failures roll back session mutations instead of returning false success.

### Database

- Added `V14__create_refresh_token_sessions.sql` for refresh-token families and records.

### Completed milestone work

- [#80](https://github.com/nursena-pc/payflow/issues/80) chore: start v0.8.0 development
- [#82](https://github.com/nursena-pc/payflow/issues/82) feat(auth): add secure refresh-token cryptography adapters
- [#84](https://github.com/nursena-pc/payflow/issues/84) feat(auth): issue refresh credentials on login
- [#86](https://github.com/nursena-pc/payflow/issues/86) feat(auth): rotate refresh credentials
- [#88](https://github.com/nursena-pc/payflow/issues/88) feat(auth): revoke refresh-token family on reuse
- [#90](https://github.com/nursena-pc/payflow/issues/90) feat(auth): revoke current refresh-token session on logout

[0.8.0]: https://github.com/nursena-pc/payflow/compare/v0.7.0...v0.8.0
[0.9.0]: https://github.com/nursena-pc/payflow/compare/v0.8.0...v0.9.0
[0.10.0]: https://github.com/nursena-pc/payflow/compare/v0.9.0...v0.10.0
[0.11.0]: https://github.com/nursena-pc/payflow/compare/v0.10.0...v0.11.0
[0.12.0]: https://github.com/nursena-pc/payflow/compare/v0.11.0...v0.12.0
[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.12.0...HEAD
