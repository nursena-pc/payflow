# Delivery Roadmap

## Current delivery focus

PayFlow v0.13.0 is the latest tagged release. The active development line uses
the Maven version `0.14.0-SNAPSHOT`.

The v0.13.0 account-recovery and secure-mail-delivery release was published
from verified merge commit `726f631a0de800870813ccb0c00b2676eb5d172b`
through successful release workflow run `31115952987`. The v0.14.0 milestone
adds TOTP multi-factor authentication, digest-only recovery codes, and bounded
step-up authentication while preserving the existing anti-enumeration,
refresh-session, and logging boundaries.

PayFlow remains a modular monolith. PostgreSQL is the system of record; Redis is
used only for bounded, explicitly expiring abuse-control state.

## Delivered platform baseline

### Repository and architecture

- [x] Java 21 and Spring Boot 3.5 baseline
- [x] Modular-monolith package convention
- [x] Inward-facing domain, application, and adapter dependencies
- [x] PostgreSQL, Redis, and Kafka local infrastructure
- [x] Flyway-managed database schema
- [x] CI, Dependabot, pull-request, and issue templates
- [x] Conventional Commits and protected pull-request workflow
- [x] Tag-triggered GitHub Releases with executable JAR and SHA-256 assets

### Identity, authorization, and sessions

- [x] User registration with normalized email addresses
- [x] BCrypt password hashing
- [x] RSA-signed JWT access tokens
- [x] Opaque refresh credentials persisted only as SHA-256 digests
- [x] Atomic refresh-token rotation
- [x] Concurrent rotation protection
- [x] Refresh-token reuse detection and family revocation
- [x] Current-session logout
- [x] Authenticated all-session logout
- [x] Durable all-session revocation across application restarts
- [x] Stable authentication and authorization error contracts
- [x] ADMIN-derived `PAYFLOW_OPERATIONS` authorization boundary
- [x] Stable JWT `kid` issuance with RS256 algorithm pinning
- [x] Active and previous signing-key verification overlap
- [x] Production fail-fast validation for configured RSA key material

### Login protection

- [x] Distributed identity and direct-peer fixed-window counters
- [x] SHA-256-hashed Redis key material
- [x] Atomic Lua counter updates
- [x] Stable `429` response with positive `Retry-After`
- [x] Fail-closed `503` when Redis cannot make a safe decision
- [x] Real Redis threshold, expiration, reset, and concurrency verification
- [x] Low-cardinality metrics and credential-free security events
- [x] Dedicated operations guide and Postman verification workflow

### Wallets, transfers, ledger, and events

- [x] One wallet per authenticated user
- [x] Simulated top-up and current-wallet retrieval
- [x] Optimistic locking and PostgreSQL concurrency verification
- [x] Authenticated, atomic wallet-to-wallet transfers
- [x] Source-wallet-scoped idempotency
- [x] Immutable double-entry ledger persistence
- [x] Transaction rollback and concurrent duplicate verification
- [x] Paginated and filterable transaction history
- [x] Transactional outbox and retryable Kafka publication
- [x] Idempotent event consumption
- [x] Durable dead-letter intake, replay, and discard lifecycle
- [x] Append-only operator command auditing
- [x] Trustworthy request correlation with strict inbound identifier validation
- [x] Structured JSON logging with centralized sensitive-value masking
- [x] One bounded request-completion event per synchronous HTTP request

## v0.9.0 — Released

- [x] Merge Redis-backed login protection through PR #99
- [x] Pass protected-branch CI
- [x] Merge release preparation through PR #100
- [x] Publish versioned release notes
- [x] Tag the verified merge commit as `v0.9.0`
- [x] Publish `payflow-0.9.0.jar`
- [x] Publish and verify `payflow-0.9.0.jar.sha256`
- [x] Publish the GitHub Release

## v0.10.0 — Released: Trusted Client Context

### Product outcome

Security controls can identify the effective client address when PayFlow is
deployed behind known reverse proxies, while untrusted peers cannot influence
that identity by supplying forwarding headers.

### Increment 1 — Threat model and configuration

- [x] Open the v0.10.0 implementation issue
- [x] Define trusted-proxy CIDR configuration
- [x] Validate IPv4 and IPv6 network ranges at startup
- [x] Define forwarding-header precedence explicitly
- [x] Bound accepted header length and proxy-hop count
- [x] Document direct-peer fallback and failure behavior

### Increment 2 — Client-address resolver

- [x] Introduce an application-facing client-context abstraction
- [x] Keep servlet and header parsing inside the inbound adapter
- [x] Ignore forwarding headers when the direct peer is not trusted
- [x] Parse trusted proxy chains from right to left
- [x] Select the first untrusted address as the effective client
- [x] Normalize IPv4 and IPv6 literals without DNS resolution
- [x] Reject or safely fall back on malformed and obfuscated identifiers

### Increment 3 — Login-protection integration

- [x] Replace direct `HttpServletRequest#getRemoteAddr` coupling
- [x] Feed the resolved effective client into the existing rate-limit port
- [x] Preserve identity-counter and client-counter semantics
- [x] Preserve generic `401`, stable `429`, and fail-closed `503` contracts
- [x] Add bounded decision metrics for source and fallback outcome
- [x] Keep raw client addresses out of metric labels and logs

### Increment 4 — Verification

- [x] Verify spoofed forwarding headers are ignored from untrusted peers
- [x] Verify a single trusted proxy
- [x] Verify multi-hop trusted and untrusted proxy chains
- [x] Verify IPv4, IPv6, and mixed-address chains
- [x] Verify malformed, oversized, and excessive-hop inputs
- [x] Verify direct-peer fallback
- [x] Verify login rate limiting groups requests by effective client
- [x] Run the complete Maven verification suite

### Increment 5 — Public and operational contracts

- [x] Add an ADR for the proxy trust model
- [x] Update deployment and login-protection documentation
- [x] Add reverse-proxy configuration examples
- [x] Update architecture diagrams where the trust boundary is shown
- [x] Pass protected-branch CI and review checks
- [x] Prepare v0.10.0 release notes
- [x] Merge v0.10.0 release preparation through protected PR #104
- [x] Tag merge commit `9dad6bdf0b8d1e166ba6454a6d791561cc30b671` as `v0.10.0`
- [x] Publish `payflow-0.10.0.jar`
- [x] Publish and verify `payflow-0.10.0.jar.sha256`
- [x] Publish the GitHub Release

### Publication record

- release workflow run: `30675532483`
- executable JAR size: `98,655,970` bytes
- verified SHA-256: `174D7F51D27F19B0A45B281869FF86BD9DC52F59B41B20B479827B92102D957B`

## Explicit v0.10.0 non-goals

- unrestricted trust of `Forwarded` or `X-Forwarded-For`
- DNS-based proxy trust
- GeoIP or location inference
- device fingerprinting
- generalized API-wide rate limiting
- request-correlation or distributed-tracing implementation
- external OAuth or OpenID Connect providers
- multi-factor authentication
- password recovery and email verification
- microservice extraction

These concerns require separate issues and threat models rather than being
silently added to trusted client-context resolution.

## v0.10.0 release exit criteria

The release is ready only when:

- [x] only configured proxy networks may influence effective client identity
- [x] spoofed forwarding headers from untrusted peers are ignored
- [x] trusted chains resolve deterministically for IPv4 and IPv6
- [x] malformed or excessive forwarding input fails safely
- [x] login rate limiting uses the effective client without changing public error contracts
- [x] raw client addresses remain excluded from metric labels and logs
- [x] focused unit, integration, and acceptance tests pass
- [x] the complete Maven suite passes
- [x] OpenAPI and operations documentation match the implementation
- [x] protected-branch CI passes for the feature merge
- [x] v0.10.0 release notes are prepared
- [x] the release-preparation pull request is merged
- [x] the v0.10.0 tag is published
- [x] the executable JAR and SHA-256 checksum are published
- [x] the GitHub Release is published

## v0.11.0 — Released: Structured Logging and Request Correlation

### Product outcome

Operators can follow a synchronous HTTP request through bounded application
logs using one trustworthy correlation identifier without exposing credentials,
personal data, financial data, raw request paths, or attacker-controlled log
structure.

### Increment 1 — Request correlation

- [x] Define a bounded correlation-ID policy
- [x] Replace absent, duplicated, malformed, oversized, and newline-bearing input
- [x] Generate a server-controlled UUID when inbound input is unusable
- [x] Return the effective identifier in every HTTP response
- [x] Include the effective identifier in centralized API error responses
- [x] Establish and clear request-lifetime MDC safely

### Increment 2 — Structured logging and redaction

- [x] Add single-line JSON logs for `structured-logging` and `production`
- [x] Define stable service, schema, severity, logger, thread, message, and correlation fields
- [x] Mask credentials, tokens, secrets, authorization values, API keys, private keys, and JWT-like values
- [x] Disable uncontrolled structured and non-structured argument expansion
- [x] Bound encoded exception length and throwable depth

### Increment 3 — Bounded HTTP completion events

- [x] Emit exactly one `http.request.completed` event per synchronous request
- [x] Use the Spring MVC route template instead of the raw request URI
- [x] Bound method, status, duration, and outcome fields
- [x] Use stable `UNMATCHED` and `UNKNOWN` fallback values
- [x] Exclude bodies, query strings, headers, identifiers, balances, and amounts
- [x] Keep correlation IDs out of metric labels

### Increment 4 — Public and operational contracts

- [x] Document the global `X-Correlation-ID` response header in OpenAPI
- [x] Add executable Postman response-correlation verification
- [x] Document activation, field schema, redaction, and exception policy
- [x] Document synchronous and asynchronous propagation boundaries
- [x] Verify production-profile response correlation and JSON logs in Docker

### Increment 5 — Verification and release preparation

- [x] Merge the observability increment through protected PR #108
- [x] Close implementation issue #107 after merge
- [x] Pass 47 focused observability acceptance tests
- [x] Pass 1,017 complete Maven tests with zero failures and zero errors
- [x] Produce 215 Surefire XML reports
- [x] Pass protected `build-and-test` CI
- [x] Pass production-profile Docker smoke verification
- [x] Prepare v0.11.0 release notes
- [x] Merge v0.11.0 release preparation through protected PR #111
- [x] Pass 1,022 complete release-candidate tests with zero failures and zero errors
- [x] Tag merge commit `00401d55546fb819fe7d96a8fad8e8c43e37649c` as `v0.11.0`
- [x] Publish `payflow-0.11.0.jar`
- [x] Publish and independently verify `payflow-0.11.0.jar.sha256`
- [x] Publish the GitHub Release

### Publication record

- release workflow run: `30816366250`
- executable JAR size: `99,121,200` bytes
- verified SHA-256: `AFA7836636F034BEA0CF8281851C1619E183B2AAEAC4F3C14D3FA39F40F7ABD0`

## Explicit v0.11.0 non-goals

- distributed tracing implementation
- implicit servlet-MDC propagation to scheduled jobs, outbox publishers, Kafka consumers, retries, or dead-letter execution
- request or response body logging
- raw URI, query-string, cookie, authorization-header, or forwarding-header logging
- user, wallet, transfer, balance, amount, or idempotency-key logging
- correlation IDs as authentication, authorization, business, transaction, partition, or metric-label inputs
- signing-key rotation or external key-management integration
- password recovery, verified-email workflows, or multi-factor authentication
- generalized API-wide abuse protection

These concerns require separate issues, threat models, and versioned contracts
rather than being silently added to the observability increment.

## v0.11.0 release exit criteria

The release is ready only when:

- [x] invalid or attacker-controlled correlation input is replaced safely
- [x] every synchronous response returns one effective correlation identifier
- [x] centralized API errors expose the same effective identifier
- [x] production-oriented logs are valid single-line JSON
- [x] sensitive values are redacted and request-completion fields remain bounded
- [x] focused unit, integration, acceptance, OpenAPI, Postman, and Docker checks pass
- [x] the complete Maven suite passes
- [x] protected CI passes for the feature merge
- [x] v0.11.0 release notes are prepared
- [x] the release-preparation pull request is merged
- [x] the v0.11.0 tag is published
- [x] the executable JAR and SHA-256 checksum are published and independently verified
- [x] the GitHub Release is published

## v0.12.0 — Released: JWT Signing-Key Rotation

### Product outcome

PayFlow can rotate RSA signing keys without invalidating every unexpired access
token at deployment time. Newly issued tokens identify the active key with a
bounded `kid`; verification accepts only the configured active and previous
keys and only RS256 signatures.

### Increment 1 — Threat model and provider boundary

- [x] Open the dedicated v0.12.0 implementation issue #112
- [x] Introduce a JWT key-provider boundary outside the application and domain layers
- [x] Separate the active signing key from verification-only previous keys
- [x] Bound key IDs to a strict 64-character alphabet
- [x] Reject duplicate key IDs and aliased RSA key material

### Increment 2 — Issuance and verification

- [x] Issue every access token with the active stable `kid`
- [x] Pin signing and verification to RS256
- [x] Verify tokens signed by the active or previous configured key
- [x] Reject missing, unknown, duplicated, or untrusted key identifiers
- [x] Preserve issuer, lifetime, subject, email, and role claim contracts

### Increment 3 — Local configured-key adapter

- [x] Load PKCS#8 private keys and X.509 public keys from Spring resources
- [x] Require RSA keys of at least 2,048 bits
- [x] Prove the active public and private keys form one key pair
- [x] Fail application startup when production key configuration is incomplete or invalid
- [x] Keep ephemeral key generation limited to explicit non-production development mode
- [x] Keep private-key material out of the repository, logs, metrics, and error messages

### Increment 4 — Verification and operations

- [x] Add focused active, previous, missing-`kid`, unknown-`kid`, weak-key, and mismatch tests
- [x] Generate temporary production-profile keys during Docker smoke verification
- [x] Pass the complete Maven verification suite through protected CI
- [x] Pass protected `build-and-test` and Docker smoke CI for PR #113
- [x] Document staged rotation, rollback, key retirement, and emergency recovery

### Increment 5 — Public contracts and release

- [x] Merge the JWT signing-key rotation increment through protected PR #113
- [x] Close implementation issue #112 after merge
- [x] Add an ADR for the signing-key provider and overlap model
- [x] Update README, configuration examples, and architecture documentation
- [x] Prepare v0.12.0 release notes after the implementation PR is merged
- [x] Merge v0.12.0 release preparation through protected PR #114
- [x] Tag merge commit `fb0f97d076864cf3e45aabe0e3c25c81520ee101` as `v0.12.0`
- [x] Publish `payflow-0.12.0.jar`
- [x] Publish and independently verify `payflow-0.12.0.jar.sha256`
- [x] Publish the GitHub Release

## Explicit v0.12.0 non-goals

- remote KMS, HSM, Vault, or cloud-provider integration
- dynamic hot reload, polling, or push-based key refresh
- a public JWKS endpoint or remote JWKS consumption
- accepting more than the active and immediately previous verification key
- JWT encryption, symmetric signing, or algorithm negotiation
- access-token revocation before the existing short expiry
- refresh-token format or rotation changes
- password recovery, verified-email workflows, or multi-factor authentication

These concerns require separate issues and threat models. The v0.12.0 provider
boundary permits a future external adapter without placing Nimbus, Spring, file,
or KMS types in PayFlow application or domain code.

## v0.12.0 release exit criteria

The release is ready only when:

- [x] new access tokens carry the configured active `kid`
- [x] active and previous keys verify during the bounded overlap window
- [x] tokens with missing or unknown key IDs fail authentication
- [x] only RS256 signatures are accepted
- [x] production startup fails closed on missing, malformed, weak, or mismatched keys
- [x] private-key material remains outside source control and observable output
- [x] focused and complete Maven verification pass through protected CI
- [x] production-profile Docker smoke and protected CI pass
- [x] OpenAPI, operations documentation, ADRs, and implementation agree
- [x] v0.12.0 release preparation and publication gates complete

### Publication record

- release workflow run: `30921514114`
- executable JAR size: `99,140,599` bytes
- verified SHA-256: `BA0BF76D07B3426E9C8DDE5E128A0C7B957807F71AA982EDC5927077980AB391`

## v0.13.0 — Released: Account Recovery and Secure Mail Delivery

### Product outcome

PayFlow users can prove ownership of their normalized email address and recover
access after forgetting a password without exposing whether an account exists.
Every account-action credential is opaque, single-use, time-limited, and stored
only as a digest. A successful password recovery changes the BCrypt hash and
revokes every active refresh-token family atomically. Provider-ready messages
are protected before persistence and delivered after commit through a leased
SMTP outbox.

### Increment 1 — Domain model and migration policy

- [x] Open the dedicated v0.13.0 implementation issue under release train #106
- [x] Add nullable `email_verified_at` as an invariant separate from `UserStatus`
- [x] Backfill every pre-v0.13.0 user as verified to prevent migration lockout
- [x] Register new users without a verified-email timestamp
- [x] Add explicit `verifyEmail` and `changePassword` domain behavior
- [x] Keep password mutation unavailable outside the recovery use case
- [x] Add Flyway V15 with constrained account-action token persistence

### Increment 2 — Opaque account-action credentials

- [x] Generate at least 256 bits of cryptographically secure randomness
- [x] Use strict canonical unpadded Base64 URL encoding
- [x] Persist only fixed-length SHA-256 digests, never plaintext credentials
- [x] Separate `EMAIL_VERIFICATION` and `PASSWORD_RECOVERY` purposes
- [x] Enforce purpose-specific expiration and one successful consumption
- [x] Invalidate prior active credentials for the same user and purpose
- [x] Lock credential consumption so concurrent confirmation has one winner
- [x] Exclude credentials and digests from logs, metrics, traces, errors, and APIs

### Increment 3 — Email-verification workflow

- [x] Issue a verification credential after successful registration
- [x] Add generic `POST /api/v1/auth/email-verification/requests`
- [x] Add token-confirmation `POST /api/v1/auth/email-verification/confirm`
- [x] Build links only from validated configuration, never request host headers
- [x] Mark email ownership exactly once in the confirmation transaction
- [x] Reject login for unverified new users only after credentials match
- [x] Preserve generic behavior for unknown, closed, or already-verified accounts

### Increment 4 — Password-recovery workflow

- [x] Add generic `POST /api/v1/auth/password-recovery/requests`
- [x] Add token-confirmation `POST /api/v1/auth/password-recovery/confirm`
- [x] Reuse the registration password-strength and BCrypt policy
- [x] Consume the recovery credential and replace the password hash atomically
- [x] Revoke all active refresh-token families with `PASSWORD_RECOVERY`
- [x] Preserve the existing short access-token residual-validity boundary
- [x] Keep invalid, expired, consumed, and superseded token errors indistinguishable

### Increment 5 — Protected mail outbox and SMTP delivery

- [x] Introduce an application-facing mail port and SMTP adapter
- [x] Keep SMTP, templates, protection, and dispatch outside domain code
- [x] Persist mail atomically with credential issuance but deliver only after commit
- [x] Protect provider-ready mail bodies with AES-256-GCM before persistence
- [x] Require a configured 32-byte protection key in production
- [x] Claim work with PostgreSQL leases and `FOR UPDATE SKIP LOCKED`
- [x] Apply bounded retry without scheduling beyond credential expiry
- [x] Erase protected content after success, terminal failure, or supersession
- [x] Use stable `Message-ID` values and document bounded duplicate risk
- [x] Keep recipients, links, credentials, digests, and protected bytes out of logs and metrics

### Deferred to the generalized abuse-protection milestone

The generic request responses and anti-enumeration behavior are part of
v0.13.0. Purpose-specific Redis quotas of 3 requests per normalized identity
per hour and 20 requests per effective client per hour, hashed limiter
dimensions, fail-closed limiter outages, and low-cardinality limiter outcomes
are intentionally deferred. They are not claimed by this release.

### Increment 6 — Verification and public contracts

- [x] Unit-test domain state, credential shape, digesting, and lifetime policy
- [x] Verify Flyway V14-to-V17 upgrades and clean installation with PostgreSQL
- [x] Verify concurrent confirmation and transactional rollback with PostgreSQL
- [x] Verify protected mail persistence and SMTP construction without exposing credentials in diagnostics
- [x] Add MockMvc, real endpoint-to-database, OpenAPI, and Postman contracts
- [x] Add an ADR and operations guide for protected account-action mail delivery
- [x] Run the complete Maven verification suite and production Docker smoke
- [x] Pass protected `build-and-test` and `docker-smoke` checks for PRs #121, #123, and #125

## Explicit v0.13.0 non-goals

- email-address change or multiple email addresses per user
- multi-factor authentication, recovery codes, or step-up authentication
- external OAuth, OpenID Connect, or social-login providers
- access-token denylisting or immediate revocation of already-issued JWTs
- browser cookies, CSRF policy, frontend pages, mobile deep links, or UI branding
- durable storage of plaintext credentials or provider-ready reset URLs
- provider-side exactly-once guarantees, attachments, localization, or automatic mail-key rotation
- purpose-specific Redis request quotas and generalized API-wide rate limiting
- device fingerprinting or behavioral risk scoring
- remote KMS, HSM, Vault, Kubernetes, or microservice extraction

These concerns require separate threat models and versioned contracts. The
v0.13.0 is limited to ownership verification, password recovery, bounded
email delivery, and the security evidence needed to trust them.

## v0.13.0 release exit criteria

The release is ready only when:

- [x] pre-v0.13.0 users remain able to authenticate after migration
- [x] newly registered users cannot authenticate before email verification
- [x] account-action plaintext and digests never enter observable output
- [x] request responses do not disclose account existence or eligibility
- [x] expired, consumed, superseded, and malformed credentials fail safely
- [x] concurrent confirmation permits at most one successful state transition
- [x] password recovery changes the hash and revokes all refresh families atomically
- [x] SMTP failures do not weaken token or account-state correctness
- [x] focused unit, PostgreSQL, HTTP, OpenAPI, and Postman tests pass
- [x] the complete 1,174-test Maven suite and production Docker smoke pass
- [x] ADR, operations guide, configuration, and implementation agree
- [x] protected feature pull requests #121, #123, and #125 are merged
- [x] the protected v0.13.0 release-preparation pull request is merged
- [x] the v0.13.0 tag, JAR, checksum, and GitHub Release are published

### Release-candidate evidence

- email-verification feature commit: `66e181a124fc73d1dbe30b274d372bc88017ceeb`
- password-recovery feature commit: `c720b13b107010ed4b53c538b08b048aa8f21f98`
- secure-mail-outbox feature commit: `8baed12af2b52fcf49b87996ab75486490db565f`
- integrated feature merge commit: `01a1437b13d48ce08e477f5fa5962aa9fb113be6`
- complete verification: 1,174 tests, zero failures, zero errors
- feature-line artifact SHA-256: `214412C8FA5E6279FD9874EC935AA95B5FA90C0CD20166CCF027C7A0EC2C5191`
- release-preparation PR: `#127`
- release-candidate commit: `2d4c8b9b30b2291108da93b0df1edab97f032328`
- published merge and tag commit: `726f631a0de800870813ccb0c00b2676eb5d172b`
- annotated tag object: `9879780a418d8490b835c36b7a01cd0019621a7e`
- release workflow run: [`31115952987`](https://github.com/nursena-pc/payflow/actions/runs/31115952987)
- published at: `2026-08-06T15:35:55Z`
- published JAR size: `100015861` bytes
- published JAR SHA-256: `78520B04BA3FDAF1BCEB3EAF29FCBE96C46265DF691C52C9048CEE6B5D58F4DA`
- release checksum verification: passed
- publication-evidence JSON SHA-256: `4FDD37BC1BF5D058A391A23784CCF87DED3FADCC3F9DB564806A8A52DC1F7B51`

## v0.14.0 — Active Development: MFA and Step-Up Authentication

### Product outcome

PayFlow users can protect an email-verified account with a standards-compatible
TOTP authenticator, complete MFA during login, recover with one-time codes, and
prove recent possession of a second factor before selected account-security or
operator actions. Password verification remains the first login factor. No
access or refresh credential is issued until an enabled MFA challenge succeeds.

### Increment 1 — Threat model and lifecycle boundaries

- [x] Open the dedicated v0.14.0 implementation issue
- [ ] Record MFA enrollment, login, recovery, disable, bypass, replay, and concurrency threats
- [ ] Keep MFA state separate from `UserStatus` and email-verification state
- [ ] Define `DISABLED`, `PENDING`, and `ENABLED` lifecycle transitions explicitly
- [ ] Define stable public errors that do not reveal secrets, recovery-code state, or internal challenge state
- [ ] Define account-security refresh-family revocation reasons before implementation
- [ ] Keep controllers, JWT adapters, and JPA entities outside the MFA domain model

### Increment 2 — TOTP enrollment and secret protection

- [ ] Require an authenticated, active, email-verified user to begin enrollment
- [ ] Generate a high-entropy TOTP secret with a standards-compatible `otpauth://` provisioning value
- [ ] Protect every pending or active TOTP secret before PostgreSQL persistence
- [ ] Use a dedicated MFA secret-protection port and separate production key material
- [ ] Return the plaintext provisioning secret only in the enrollment response that created it
- [ ] Activate enrollment only after a valid TOTP proof within the documented clock-skew window
- [ ] Serialize replacement so one user has at most one effective pending or active authenticator
- [ ] Exclude secrets, provisioning URIs, TOTP values, protected bytes, and key material from observable output

### Increment 3 — MFA login challenge

- [ ] Preserve existing Redis-backed password-attempt protection before user lookup and password verification
- [ ] Issue a short-lived opaque MFA login challenge only after the password and account eligibility checks succeed
- [ ] Persist only a fixed-length challenge digest, expiration, bounded attempt state, and terminal state
- [ ] Issue no access or refresh credential while an enabled user's challenge remains unresolved
- [ ] Consume a successful challenge exactly once before issuing access and refresh credentials
- [ ] Permit one documented TOTP clock step on either side of the current step
- [ ] Reject expired, exhausted, replayed, malformed, and superseded challenges through one stable public contract
- [ ] Lock verification so concurrent submissions have at most one successful winner

### Increment 4 — Recovery codes and MFA disable

- [ ] Generate recovery codes from cryptographically secure randomness
- [ ] Return plaintext recovery codes once at activation or explicit rotation
- [ ] Persist only fixed-length recovery-code digests
- [ ] Consume every recovery code atomically and at most once
- [ ] Make recovery-code and TOTP challenge failures indistinguishable at the public boundary
- [ ] Require recent step-up proof before recovery-code rotation or MFA disable
- [ ] Revoke active refresh-token families after MFA disable or secret replacement
- [ ] Preserve append-only, credential-free account-security audit evidence

### Increment 5 — Step-up authentication

- [ ] Introduce an application-facing step-up policy independent from controller annotations
- [ ] Bind every step-up grant to one authenticated subject, purpose, issue time, and short expiration
- [ ] Require a recent second-factor proof for MFA disable and recovery-code rotation
- [ ] Evaluate dead-letter replay and discard as explicit operator step-up candidates
- [ ] Reject cross-purpose, expired, replayed, or wrong-subject grants
- [ ] Keep step-up grants out of logs, metric labels, audit payloads, and persistence plaintext
- [ ] Document which operations remain bearer-only and which require step-up

### Increment 6 — Verification and public contracts

- [ ] Unit-test TOTP vectors, clock skew, lifecycle transitions, protection, digesting, and redaction
- [ ] Verify clean Flyway installation and upgrades from the v0.13.0 schema with PostgreSQL
- [ ] Verify enrollment replacement, challenge consumption, recovery-code use, and disable races
- [ ] Add real endpoint-to-database, MockMvc, OpenAPI, and Postman contracts
- [ ] Add an MFA threat model, ADR, and operations guide
- [ ] Verify production startup fails safely without configured MFA secret-protection material
- [ ] Run the complete Maven verification suite and production Docker smoke
- [ ] Pass protected `build-and-test` and `docker-smoke` checks for every increment

## Explicit v0.14.0 non-goals

- SMS, voice-call, or email-delivered one-time passwords
- WebAuthn, passkeys, FIDO2 security keys, or biometric authentication
- external OAuth, OpenID Connect, SAML, or social-login providers
- trusted-device cookies, remember-this-device behavior, or device fingerprinting
- behavioral analytics, geolocation risk, impossible-travel detection, or adaptive authentication
- generalized registration, refresh, recovery, or operations rate-limit policy; that remains a v0.15.0 concern
- frontend QR rendering, mobile deep-link UX, or authenticator-app branding
- remote KMS, HSM, Vault, Kubernetes, or microservice extraction
- access-token denylisting or immediate revocation of already-issued JWTs

These concerns require separate threat models and versioned contracts. v0.14.0
is limited to TOTP enrollment, MFA login completion, single-use recovery codes,
selected step-up policies, and the evidence needed to trust those boundaries.

## v0.14.0 release exit criteria

The release is ready only when:

- [ ] TOTP secrets are never stored or emitted as durable plaintext
- [ ] enabled MFA prevents access and refresh issuance until the second factor succeeds
- [ ] login challenges are short-lived, digest-only, attempt-bounded, and single-use
- [ ] recovery codes are returned once, stored only as digests, and consumed once
- [ ] concurrent enrollment, challenge, recovery, rotation, and disable operations preserve one valid outcome
- [ ] account-security changes revoke active refresh-token families as documented
- [ ] selected step-up operations reject wrong-subject, wrong-purpose, expired, and replayed grants
- [ ] errors, logs, metrics, traces, and audits expose no MFA credentials or protected secret material
- [ ] focused unit, PostgreSQL, HTTP, OpenAPI, and Postman tests pass
- [ ] the complete Maven suite and production Docker smoke pass
- [ ] threat model, ADR, operations guide, configuration, and implementation agree
- [ ] protected feature and release-preparation pull requests are merged
- [ ] the v0.14.0 tag, JAR, checksum, and GitHub Release are published

## Later v1.0 candidates

Later v1.0 candidates include generalized abuse protection, load/performance
evidence, backup/restore rehearsal, API freeze, SBOM generation, and release
stabilization.
