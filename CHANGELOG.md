<!-- payflow-release-v0.8.0 -->
# Changelog

All notable PayFlow changes are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and PayFlow uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.15.0] - 2026-08-18

### Added

- Application-facing generalized abuse-protection policy with five bounded workflow identifiers, validated endpoint-specific windows/limits, and explicit dependency-failure modes under `payflow.security.abuse-protection`.
- Validated endpoint-specific windows and limits were established with the Increment 1 foundation tracked by issue #151.
- Shared atomic Redis enforcement with expiring, domain-separated digest-only identity/client keys, TTL repair, bounded cardinality, real-Redis concurrency verification, and explicit `FAIL_CLOSED` / `FAIL_OPEN` handling.
- Generalized protection for email-verification requests, password-recovery requests, MFA login-challenge confirmation, and step-up grant issuance while preserving the trusted effective-client-address boundary.
- Bounded Micrometer abuse-decision and Redis-failure metrics, a dedicated Grafana dashboard, actionable Prometheus alerts, and credential-safe operations guidance.
- Pinned external load tooling, reproducible protected-workflow scenarios, frozen steady/saturation/overload budgets, quota-pressure evidence, recovery checks, and reviewed performance evidence under `docs/performance/evidence/`.
- Bounded registration performance experiment and committed decision evidence at `docs/performance/evidence/2026-08-17-registration-defer-f94ffa8.md`.

### Changed

- Prepared the v0.15.0 release candidate at Maven version `0.15.0` while preserving milestone issue #149 and release-finalization issue #166.
- Aligned OpenAPI and Postman descriptions with the implemented coarse quota/dependency behavior for protected account-action, MFA challenge, and step-up workflows.
- Added source-user email-verification request coverage to the standard Postman collection and aligned MFA/step-up workflow guidance.
- Aligned README, ADR 0015, threat model, policy guidance, and operations guidance with the final v0.15.0 implementation and reviewed Increment 6 evidence.

### Security

- Email-verification and password-recovery request outcomes remain empty `202 Accepted` for eligible, ineligible, quota-limited, and fail-closed dependency paths; blocked work creates no credential or delivery side effect.
- MFA challenge quota rejection reuses the existing coarse unauthorized contract, while fail-closed abuse-protection dependency failure uses the existing `MFA_SECURITY_UNAVAILABLE` boundary without sensitive mutation.
- Step-up quota rejection runs before user/authenticator locking, second-factor consumption, or grant creation/supersession.
- Generalized observability uses only bounded application-owned dimensions and excludes email addresses, user identifiers, raw client addresses, credentials, Redis keys, counters, TTL values, and raw exception detail.
- Registration remains deliberately unwired under the evidence-backed `DEFER` decision; the existing `201` / `400` / `409` registration contract is unchanged.
- The existing password-login limiter remains a separate compatibility contract with unchanged counters, keys, limits, public behavior, and metrics.

### Performance

- Accepted protected-workflow evidence satisfies the frozen developer-workstation contract, including zero quota bypass and recovery within the documented budget.
- The registration experiment observed no saturation through 16 registrations/second and therefore did not establish the material resource-exhaustion prerequisite for `ACTIVATE`.
- Performance evidence is environment-specific developer-workstation evidence and is not production capacity certification.
## [0.14.0] - 2026-08-12

### Added

- Package-bounded MFA lifecycle with explicit `DISABLED`, `PENDING`, and `ENABLED` transitions.
- Authenticated TOTP enrollment, status, confirmation, and pending-cancellation endpoints.
- AES-256-GCM-protected TOTP secret persistence through PostgreSQL V18.
- Password-first, digest-only MFA login challenges through PostgreSQL V19.
- Ten one-time 128-bit Base64URL recovery codes stored only as SHA-256 digests through PostgreSQL V20.
- Purpose-bound, single-use step-up grants stored only as digests through PostgreSQL V21.
- Transactional MFA disable with refresh-session revocation and append-only audit evidence.
- Transactional recovery-code rotation with one-time replacement-code disclosure.
- Authenticated REST endpoints for step-up issuance, MFA disable, and recovery-code rotation.

### Security

- Enabled MFA prevents access and refresh credential issuance until a valid TOTP or unused recovery code consumes the login challenge.
- TOTP secrets, recovery codes, challenge tokens, step-up grants, and their digests remain outside logs, metrics, traces, errors, and audit payloads.
- Wrong-subject, wrong-purpose, expired, superseded, malformed, unknown, and replayed step-up grants share stable coarse failure contracts.
- MFA disable consumes an exact `mfa-disable` grant, deletes authenticator and recovery-code state, revokes active refresh-token families, and appends audit evidence atomically.
- Recovery-code rotation consumes an exact `recovery-code-rotation` grant and replaces the complete digest set atomically.
- Plaintext provisioning material and recovery codes cross the API boundary only in the response that creates them.
- Active-authenticator replacement remains explicitly deferred until a safe two-stage replacement lifecycle is designed and verified.

### Database

- V18 adds protected MFA authenticator persistence.
- V19 adds digest-only MFA login challenges.
- V20 adds digest-only, single-use recovery codes.
- V21 adds purpose-bound step-up grants.
- V22 and V24 add constrained account-security audit support.
- V23 adds the `MFA_DISABLED` refresh-family revocation reason.

### Completed milestone work

- [#133](https://github.com/nursena-pc/payflow/pull/133) — MFA lifecycle foundation
- [#135](https://github.com/nursena-pc/payflow/pull/135) — TOTP enrollment and secret protection
- [#138](https://github.com/nursena-pc/payflow/pull/138) — MFA login challenge
- [#140](https://github.com/nursena-pc/payflow/pull/140) — recovery codes
- [#142](https://github.com/nursena-pc/payflow/pull/142) — purpose-bound step-up authentication
- [#144](https://github.com/nursena-pc/payflow/pull/144) — transactional MFA disable
- [#145](https://github.com/nursena-pc/payflow/pull/145) — recovery-code rotation
- [#146](https://github.com/nursena-pc/payflow/pull/146) — MFA disable HTTP adapter
## [0.13.0] - 2026-08-06


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

### Completed milestone work

- [#120](https://github.com/nursena-pc/payflow/issues/120) / [#121](https://github.com/nursena-pc/payflow/pull/121) — email-verification workflow
- [#122](https://github.com/nursena-pc/payflow/issues/122) / [#123](https://github.com/nursena-pc/payflow/pull/123) — password-recovery workflow
- [#124](https://github.com/nursena-pc/payflow/issues/124) / [#125](https://github.com/nursena-pc/payflow/pull/125) — secure mail outbox and SMTP delivery

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
[0.13.0]: https://github.com/nursena-pc/payflow/compare/v0.12.0...v0.13.0
[0.14.0]: https://github.com/nursena-pc/payflow/compare/v0.13.0...v0.14.0
[0.15.0]: https://github.com/nursena-pc/payflow/compare/v0.14.0...v0.15.0
[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.15.0...HEAD
