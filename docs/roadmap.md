# Delivery Roadmap

## Current delivery focus

PayFlow v0.16.0 is the latest tagged and published release. The immutable publication is anchored to annotated tag `v0.16.0` with tag object `8308e190960525924a550dafc8dcfcf61d4250d0` and exact merge/tag target `da8cefa9772d8e009b5ef1e5ab53d03bc44b1c13`. Tag-triggered Release workflow run `32757038003` succeeded and published GitHub Release ID `375880233` at `2026-08-24T17:40:22Z`.

The published `100566879`-byte `payflow-0.16.0.jar` has independently verified SHA-256 `8c542fc6928179345e5cda3d0f66d1481f7277a88096a52a69952ed95f2958e6`. Checksum asset SHA-256 `b14f5ea137012e7aa8557fa21c1c9fece151deb2447a0b636da7ee3a173d14b0` names and matches that JAR, and the published release notes exactly match the reviewed `docs/releases/v0.16.0.md`.

The v0.16.0 stabilization line tracked by issue `#169` is complete through protected finalization and independently verified immutable publication. Issue `#186` remains open only for the publication-record merge and release-train closure. Registration remains evidence-backed `DEFER`, the existing password-login limiter semantics remain unchanged, and the publication does not introduce signing, SLSA, reproducible-build, provenance-attestation, production-certification, or real-money claims.

The v0.15.0 generalized abuse-protection release remains anchored to verified
merge commit `c29a067ca3a64514444e17db59a2b862d26f5950` and successful release workflow run `32172653513`.
The published `100236578`-byte JAR has independently verified SHA-256
`7EDF5EAD1EB93966E750F917D9472B4383D2B3CDA7406A264AE78B106A779080`.

The v0.14.0 publication remains anchored to merge/tag commit
`d65929b98bb66b22f208d26f75a764e1ade78b6a` and release workflow run `31728977714`.
The existing anti-enumeration, trusted-client, credential-redaction, and
modular-monolith boundaries remain unchanged.
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

## v0.14.0 — Released: MFA and Step-Up Authentication

### Product outcome

PayFlow users can protect an email-verified account with a standards-compatible
TOTP authenticator, complete MFA during login, recover with one-time codes, and
prove recent possession of a second factor before selected account-security or
operator actions. Password verification remains the first login factor. No
access or refresh credential is issued until an enabled MFA challenge succeeds.

### Increment 1 — Threat model and lifecycle boundaries

- [x] Open the dedicated v0.14.0 implementation issue
- [x] Record MFA enrollment, login, recovery, disable, bypass, replay, and concurrency threats
- [x] Keep MFA state separate from `UserStatus` and email-verification state
- [x] Define `DISABLED`, `PENDING`, and `ENABLED` lifecycle transitions explicitly
- [x] Define stable public errors that do not reveal secrets, recovery-code state, or internal challenge state
- [x] Define account-security refresh-family revocation reasons before implementation
- [x] Keep controllers, JWT adapters, and JPA entities outside the MFA domain model

The accepted foundation is documented in [ADR 0014](adr/0014-mfa-and-step-up-authentication.md)
and the [MFA threat model](security/mfa-threat-model.md). The domain now freezes the
lifecycle state machine, typed step-up purposes, and dedicated `MFA_DISABLED` and
`MFA_AUTHENTICATOR_REPLACED` refresh-family revocation reasons without adding
MFA persistence, endpoints, TOTP verification, or runtime step-up enforcement.

### Increment 2 — TOTP enrollment and secret protection

- [x] Require an authenticated, active, email-verified user to begin enrollment
- [x] Generate a high-entropy TOTP secret with a standards-compatible `otpauth://` provisioning value
- [x] Protect every pending or active TOTP secret before PostgreSQL persistence
- [x] Use a dedicated MFA secret-protection port and separate production key material
- [x] Return the plaintext provisioning secret only in the enrollment response that created it
- [x] Activate enrollment only after a valid TOTP proof within the documented clock-skew window
- [x] Serialize replacement so one user has at most one effective pending or active authenticator
- [x] Exclude secrets, provisioning URIs, TOTP values, protected bytes, and key material from observable output

The enrollment implementation uses V18 `mfa_authenticators` persistence with one
row per user, pessimistic user/authenticator locking, a ten-minute pending
enrollment lifetime, 160-bit TOTP secrets, RFC 4226/6238 HMAC-SHA1 with six
digits and a ±1 time-step verification window, and AES-256-GCM protection
bound to the owning user identifier. Starting enrollment requires the current
password in addition to an authenticated bearer context; overlapping pending or
enabled enrollment attempts return the stable `MFA_STATE_CONFLICT` contract.
Cancelling a pending enrollment deletes the protected secret row.

### Increment 3 — MFA login challenge

- [x] Preserve existing Redis-backed password-attempt protection before user lookup and password verification
- [x] Issue a short-lived opaque MFA login challenge only after the password and account eligibility checks succeed
- [x] Persist only a fixed-length challenge digest, expiration, bounded attempt state, and terminal state
- [x] Issue no access or refresh credential while an enabled user's challenge remains unresolved
- [x] Consume a successful challenge exactly once before issuing access and refresh credentials
- [x] Permit one documented TOTP clock step on either side of the current step
- [x] Reject expired, exhausted, replayed, malformed, and superseded challenges through one stable public contract
- [x] Lock verification so concurrent submissions have at most one successful winner

The login-challenge implementation uses V19 digest-only PostgreSQL persistence,
a five-minute default lifetime, five-attempt default budget, explicit terminal
states, and pessimistic user/challenge/authenticator locking. Password success
for an MFA-enabled account returns `202 MFA_REQUIRED` without creating access or
refresh credentials. `POST /api/v1/auth/mfa/challenges/confirm` consumes one
pending challenge after a valid TOTP proof and only then enters the shared
credential-issuance boundary. Unknown, malformed, expired, exhausted,
superseded, replayed, and invalid-proof outcomes share the stable
`MFA_CHALLENGE_INVALID` response.

### Increment 4 — Recovery codes

- [x] Generate recovery codes from cryptographically secure randomness
- [x] Return plaintext recovery codes once when TOTP enrollment is activated
- [x] Persist only fixed-length recovery-code digests
- [x] Consume every recovery code atomically and at most once
- [x] Make recovery-code and TOTP challenge failures indistinguishable at the public boundary
- [x] Keep recovery-code plaintext and digests out of observable output
- [x] Rotate recovery codes only after a recent purpose-bound step-up proof exists

The recovery-code implementation generates ten independent 128-bit opaque
Base64URL values when pending TOTP enrollment becomes `ENABLED`. The activation
response returns that plaintext set once while PostgreSQL V20 persists only
32-byte SHA-256 digests. Login-challenge confirmation accepts either the
existing six-digit TOTP proof or one unused recovery code. The matching recovery
row is pessimistically locked and consumed in the same transaction as challenge
consumption and credential issuance. Invalid, unknown, malformed, and already
consumed recovery proofs share `401 MFA_CHALLENGE_INVALID` with invalid TOTP.
Explicit recovery-code rotation now consumes an exact purpose-bound grant,
atomically replaces the complete digest set, and returns replacement plaintext
once.

### Increment 5 — Step-up authentication

- [x] Introduce an application-facing step-up policy independent from controller annotations
- [x] Bind every step-up grant to one authenticated subject, purpose, issue time, and short expiration
- [x] Require a recent second-factor proof before issuing account-security step-up grants
- [x] Evaluate dead-letter replay and discard as explicit operator step-up candidates
- [x] Reject cross-purpose, expired, superseded, replayed, or wrong-subject grants
- [x] Keep step-up grants out of logs, metric labels, audit payloads, and persistence plaintext
- [x] Document which operations remain bearer-only and which require step-up

The step-up implementation uses PostgreSQL V21 digest-only grant persistence,
256-bit opaque Base64URL credentials, a five-minute default lifetime, exact
`StepUpPurpose` binding, and pessimistic single-use consumption. Grant issuance
requires an enabled authenticator and a valid TOTP or unused recovery code. A
new grant supersedes the prior unconsumed grant for the same subject and purpose.
`StepUpAuthorizationPolicy` owns subject, purpose, expiry, supersession, and
replay checks without servlet or controller-annotation coupling. MFA disable and recovery-code rotation now consume exact grants through their
application services and authenticated HTTP adapters. Authenticator replacement
remains deferred; Kafka replay/discard remain documented operator step-up
candidates without changing their current runtime authorization.

### Increment 6 — MFA disable and recovery-code rotation

- [x] Require the exact recent step-up purpose before MFA disable or recovery-code rotation
- [x] Rotate the complete recovery-code set atomically and return replacement plaintext once
- [x] Disable the enabled authenticator only after `mfa-disable` step-up succeeds
- [ ] Implement safe two-stage authenticator replacement after v0.14.0
- [x] Revoke active refresh-token families after MFA disable
- [ ] Revoke active refresh-token families after future authenticator replacement
- [x] Preserve append-only, credential-free account-security audit evidence

### Increment 7 — Verification and public contracts

- [x] Unit-test TOTP vectors, clock skew, lifecycle transitions, protection, digesting, and redaction
- [x] Verify clean Flyway installation and upgrades from the v0.13.0 schema with PostgreSQL
- [x] Verify enrollment replacement, challenge consumption, recovery-code use, and disable races
- [x] Add real endpoint-to-database, MockMvc, OpenAPI, and Postman contracts
- [x] Add an MFA threat model, ADR, and operations guide
- [x] Verify production startup fails safely without configured MFA secret-protection material
- [x] Run the complete Maven verification suite and production Docker smoke
- [x] Pass protected `build-and-test` and `docker-smoke` checks for every increment

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
purpose-bound step-up, MFA disable, recovery-code rotation, and the evidence
needed to trust those boundaries.

## v0.14.0 release exit criteria

The release is ready only when:

- [x] TOTP secrets are never stored or emitted as durable plaintext
- [x] enabled MFA prevents access and refresh issuance until the second factor succeeds
- [x] login challenges are short-lived, digest-only, attempt-bounded, and single-use
- [x] recovery codes are returned once, stored only as digests, and consumed once
- [x] concurrent enrollment, challenge, recovery, rotation, and disable operations preserve one valid outcome
- [x] account-security changes revoke active refresh-token families as documented
- [x] selected step-up operations reject wrong-subject, wrong-purpose, expired, and replayed grants
- [x] errors, logs, metrics, traces, and audits expose no MFA credentials or protected secret material
- [x] focused unit, PostgreSQL, HTTP, OpenAPI, and Postman tests pass
- [x] the complete Maven suite and production Docker smoke pass
- [x] threat model, ADR, operations guide, configuration, and implementation agree
- [x] protected feature and release-preparation pull requests are merged
- [x] the v0.14.0 tag, JAR, checksum, and GitHub Release are published

### Publication record

- release-preparation PR: `#147`
- release-candidate commit: `1fba2dacc239d8c43149cebdf192e3086be356c3`
- published merge and tag commit: `d65929b98bb66b22f208d26f75a764e1ade78b6a`
- annotated tag object: `826c77a724915c386c375c2cc227597ae0331dda`
- release workflow run: [`31728977714`](https://github.com/nursena-pc/payflow/actions/runs/31728977714)
- published at: `2026-08-13T18:13:33Z`
- published JAR size: `100200050` bytes
- published JAR SHA-256: `A6533039C5DDBE610D9DDB986DDBDAFE192DD56BE664E86B65A72AECF51F116E`
- release checksum verification: passed

## v0.15.0 — Released: Generalized Abuse Protection and Performance Evidence

Tracking issue: [#149](https://github.com/nursena-pc/payflow/issues/149)

### Increment 1 — Threat model, policy contract, and configuration

- [x] Define protected workflows, attacker capabilities, bypass risks, and trust boundaries
- [x] Introduce an application-facing abuse-protection policy independent from controllers and servlet APIs
- [x] Define endpoint-specific per-identity and per-client limits through validated configuration
- [x] Reuse the trusted effective-client-address boundary without trusting attacker-controlled forwarding headers
- [x] Specify deterministic fail-closed or fail-open behavior for every protected workflow
- [x] Preserve generic public responses and anti-enumeration behavior under quota decisions and dependency failures
- [x] Add executable development contracts for the approved v0.15.0 scope

The accepted Increment 1 foundation is tracked by [#151](https://github.com/nursena-pc/payflow/issues/151) and documented in [ADR 0015](adr/0015-generalized-abuse-protection.md), the [generalized abuse-protection threat model](security/abuse-protection-threat-model.md), and the [policy configuration guide](abuse-protection.md). Generalized enforcement remains disabled until the shared Redis and endpoint integration increments are delivered; the existing login limiter remains unchanged.

### Increment 2 — Shared Redis enforcement foundation

- [x] Implement atomic Redis decisions with explicit expiration and bounded key cardinality
- [x] Keep raw identities, email addresses, client addresses, credentials, and proofs out of Redis keys and values
- [x] Define collision-resistant bounded identifiers for identity and client quota dimensions
- [x] Verify window boundaries, expiration, concurrency, timeout, and Redis-unavailable behavior
- [x] Preserve existing login-rate-limit behavior while sharing only approved infrastructure

Increment 2 is implemented by issue [#153](https://github.com/nursena-pc/payflow/issues/153). One Lua operation evaluates both quota dimensions, creates or repairs positive expiration, and returns the longest applicable retry delay. Redis keys contain bounded workflow and dimension prefixes plus domain-separated fixed-length digests; values contain counters only. Endpoint wiring remains deferred to later increments, and the existing login limiter remains unchanged.

### Increment 3 — Account-action request protection

- [x] Protect email-verification requests with per-identity and per-client decisions
- [x] Protect password-recovery requests with the same anti-enumeration response shape
- [x] Evaluate registration protection from documented threat and performance evidence
- [x] Verify unknown, closed, verified, and eligible accounts expose no distinguishable quota behavior
- [x] Verify concurrent requests cannot exceed the documented bounded outcome

Increment 3 is implemented by issue [#155](https://github.com/nursena-pc/payflow/issues/155). Email-verification and password-recovery requests now evaluate normalized identity and trusted client quotas before account lookup while preserving an empty `202` response for every eligibility, quota, and fail-closed outcome. Real-Redis HTTP concurrency tests bound credential and delivery side effects. Registration was evaluated and remains deferred pending Increment 6 performance evidence.

### Increment 4 — MFA challenge and step-up protection

- [x] Protect MFA login-challenge confirmation without exposing challenge or account validity
- [x] Protect step-up grant issuance without weakening subject, purpose, expiry, or single-use semantics
- [x] Keep TOTP values, recovery codes, challenge tokens, and step-up grants outside quota state and observable output
- [x] Verify abuse decisions do not consume valid single-use credentials unless the protected workflow executes

Increment 4 is implemented by issue [#159](https://github.com/nursena-pc/payflow/issues/159)
and protected pull request [#160](https://github.com/nursena-pc/payflow/pull/160).
MFA challenge confirmation now enforces fixed-length non-reversible challenge
identity and trusted-client quotas before challenge state access. Step-up grant
issuance enforces authenticated-subject and trusted-client quotas before
second-factor or grant mutation. Real-Redis HTTP/concurrency coverage proves
bounded side effects, forwarding-header resistance, Redis-key privacy, and
fail-closed dependency behavior.

### Increment 5 — Metrics, dashboards, alerts, and operations

- [x] Expose bounded decision and Redis-failure metrics without identity or client labels
- [x] Provision Grafana dashboards for quota outcomes, dependency failures, and protected-workflow health
- [x] Provision actionable alert rules with documented thresholds, duration, severity, and response guidance
- [x] Document investigation, safe mitigation, rollback, and false-positive handling
- [x] Verify logs, metrics, traces, dashboards, and alerts contain no sensitive material

Increment 5 is implemented by issue [#162](https://github.com/nursena-pc/payflow/issues/162)
and pull request [#163](https://github.com/nursena-pc/payflow/pull/163).
The delivered contract keeps Micrometer concerns adapter-side, exposes only
bounded workflow/outcome/reason/failure-mode dimensions, provisions a dedicated
abuse-protection dashboard and three actionable alerts, and documents safe
operations without weakening fail-closed defaults or the existing login limiter.

### Increment 6 — Reproducible load and performance evidence

- [x] Define latency, throughput, concurrency, saturation, and overload budgets
- [x] Add reproducible load scenarios for representative protected workflows
- [x] Record environment, dataset, duration, warm-up, measurement method, and limitations
- [x] Verify abuse protection remains effective under concurrent and overload conditions
- [x] Keep load tooling outside the normal unit-test lifecycle while retaining repeatable commands
Protected-workflow evidence is recorded under `docs/performance/evidence/`.
The bounded registration experiment produced an evidence-backed `DEFER`
decision for v0.15.0 because material resource exhaustion was not demonstrated
through the tested 16 registrations/second ceiling. Registration keeps its
existing public behavior and no generalized registration limiter is added.
### Increment 7 — Contract alignment and release preparation

- [x] Align OpenAPI, Postman, README, changelog, ADRs, threat model, and operations guidance
- [x] Add focused unit, Redis, HTTP, concurrency, redaction, and dependency-failure tests
- [x] Run the complete Maven verification suite and production Docker smoke on the exact release-candidate content
- [x] Pass protected `build-and-test` and `docker-smoke` checks on the exact release-preparation PR head
- [x] Prepare versioned release notes
- [x] Record immutable publication evidence after protected merge and publication

## Explicit v0.15.0 non-goals

- Active-authenticator replacement
- CAPTCHA or third-party bot-detection services
- CDN, WAF, API-gateway, or Kubernetes deployment
- Adaptive machine-learning risk scoring
- Frontend implementation
- Real-money operation or regulatory certification

## v0.15.0 release exit criteria

- [x] selected identity workflows enforce documented per-identity and per-client limits
- [x] anti-enumeration behavior remains stable under quota and dependency failures
- [x] Redis operations are atomic, expiring, bounded, and concurrency-tested
- [x] observable output contains no sensitive identity or credential material
- [x] latency, throughput, concurrency, and overload budgets are documented
- [x] reproducible load-test evidence satisfies the approved budgets
- [x] dashboards and actionable alerts are provisioned and verified
- [x] focused unit, Redis, HTTP, concurrency, and failure-path tests pass
- [x] the complete Maven suite and production Docker smoke pass
- [x] protected feature and release-preparation pull requests are merged
- [x] the v0.15.0 tag, JAR, checksum, and GitHub Release are published

### Publication record

- release-preparation PR: `#167`
- release-candidate commit: `2f334ca28c78533e5bfc3a2dc5ee3c4a3d903976`
- published merge and tag commit: `c29a067ca3a64514444e17db59a2b862d26f5950`
- annotated tag object: `a1aa528b4933c69a3fa81c10a103154bd1d6a327`
- release workflow run: [`32172653513`](https://github.com/nursena-pc/payflow/actions/runs/32172653513)
- published at: `2026-08-18T18:52:43Z`
- workflow artifact ID: `9338113318`
- GitHub Release ID: `372572363`
- published JAR size: `100236578` bytes
- published JAR SHA-256: `7EDF5EAD1EB93966E750F917D9472B4383D2B3CDA7406A264AE78B106A779080`
- release checksum verification: passed
- published release notes verification: exact match

## v0.16.0 — Released: Stabilization, Recovery Rehearsals, and API Freeze

Tracking issue: [#169](https://github.com/nursena-pc/payflow/issues/169)
Release-finalization issue: [#186](https://github.com/nursena-pc/payflow/issues/186)

Baseline: v0.15.0 publication-record merge
`8e1dffe61beeecca81466fee23ff217f862ce8e1`.

### Increment 1 — Stabilization baseline and compatibility contract

- [x] Open the Maven development line at `0.16.0-SNAPSHOT` through a protected PR
- [x] Inventory implemented `/api/v1` endpoints, status/error contracts, OpenAPI descriptions, and executable Postman flows
- [x] Define the v1 compatibility boundary so existing `/api/v1` request, response, and error semantics cannot change silently
- [x] Freeze existing security, privacy, fail-closed, simulated-money, and modular-monolith boundaries
- [x] Inventory stale architecture and release documentation before changing it
- [x] Add executable development contracts for the approved v0.16.0 stabilization scope

### Increment 2 — PostgreSQL backup and restore rehearsal

- [x] Define one repeatable local backup procedure for the PostgreSQL system of record
- [x] Restore into a clean isolated database/environment
- [x] Verify Flyway schema history and representative identity, session, wallet, transfer, ledger, outbox, DLQ, and audit data after restore
- [x] Verify application startup against the restored database
- [x] Document integrity checks, operator failure handling, and evidence-redaction boundaries
- [x] Keep secrets, credentials, and personal data out of committed rehearsal evidence

### Increment 3 — Flyway clean-install and upgrade rehearsal

- [x] Prove a clean database reaches the current schema through the complete migration chain
- [x] Prove an approved previous-release schema/data set upgrades to the current schema without drift
- [x] Verify Flyway history and required database invariants after migration
- [x] Document recovery and rollback boundaries without claiming unsupported down-migrations
- [x] Keep migration rehearsal commands reproducible and isolated from developer data

### Increment 4 — Redis and Kafka outage/recovery operations

- [x] Rehearse Redis outage and recovery for generalized abuse controls and the separate login limiter
- [x] Preserve existing fail-closed security behavior during dependency failure
- [x] Rehearse Kafka outage and recovery for transactional-outbox publication and consumer/DLQ paths
- [x] Verify PostgreSQL remains the durable source of truth where designed
- [x] Document observable symptoms, safe operator actions, recovery checks, and escalation conditions
- [x] Verify recovery procedures do not expose credentials, identities, raw client addresses, or payload content

### Increment 5 — API, OpenAPI, Postman, and documentation drift review

- [x] Compare implemented `/api/v1` behavior with OpenAPI and Postman contracts
- [x] Align README, architecture documentation, ADR references, security guidance, operations guides, and roadmap with implementation
- [x] Resolve stale architecture documentation that still describes delivered capabilities as planned
- [x] Add executable documentation contracts only where they prevent meaningful compatibility or operations drift
- [x] Preserve the evidence-backed registration `DEFER` decision unless new evidence and a separately reviewed change justify activation

### Increment 6 — Dependency and supply-chain evidence

- [x] Run a repository-appropriate dependency vulnerability review
- [x] Run secret scanning against committed content
- [x] Generate an SBOM with a documented tool and command
- [x] Record verifiable build/provenance inputs for the stabilization candidate
- [x] Review every critical/high finding explicitly
- [x] Do not suppress or retune findings only to make the release pass

### Increment 7 — Clean-environment release rehearsal

- [x] Verify the project from a clean checkout/environment
- [x] Run the complete Maven verification suite
- [x] Run production-profile Docker smoke on the exact reviewed stabilization head
- [x] Verify executable JAR creation and checksum generation
- [x] Verify required production configuration fails fast when intentionally incomplete
- [x] Keep rehearsal evidence separate from immutable v1.0.0 publication

### Increment 8 — v0.16.0 finalization and publication evidence

- [x] Align final README, changelog, roadmap, operations, security, OpenAPI, and Postman contracts from completed evidence only
- [x] Freeze Maven version `0.16.0` only on the reviewed release candidate
- [x] Pass protected `build-and-test` and `docker-smoke` checks on the exact release-preparation PR head
- [x] Publish the annotated `v0.16.0` tag from the exact approved merge commit
- [x] Publish and independently verify the executable JAR and SHA-256 checksum
- [x] Publish the GitHub Release from reviewed versioned release notes
- [x] Record immutable publication values only after publication

## Explicit v0.16.0 non-goals

- New wallet, transfer, payment, or transaction-history features
- New abuse-protection algorithms, quota retuning, or generalized registration activation
- Changes to the existing login-limiter semantics without a separately verified defect
- New MFA factors, OAuth/OIDC, WebAuthn/passkeys, or active-authenticator replacement
- Microservice extraction
- Kubernetes, CDN, WAF, API-gateway, or distributed production deployment
- Frontend implementation
- Real-money operation, regulatory certification, or production-capacity claims
- v1.0.0 publication itself

## v0.16.0 release exit criteria

- [x] `0.16.0-SNAPSHOT` development baseline is opened through a protected PR
- [x] `/api/v1` compatibility boundary is documented and executable where practical
- [x] PostgreSQL backup and restore rehearsal is repeatable and passes integrity checks
- [x] clean-install and previous-release-to-current Flyway rehearsals pass
- [x] Redis and Kafka outage/recovery procedures are documented and verified against existing failure contracts
- [x] OpenAPI, Postman, README, architecture docs, ADRs, security/operations guidance, and implementation agree
- [x] dependency vulnerability and secret scans have no unresolved critical/high release blockers
- [x] an SBOM and build/provenance evidence are produced for the stabilization candidate
- [x] complete Maven verification passes with zero failures, errors, and skips
- [x] production Docker smoke passes on the exact reviewed stabilization head
- [x] clean-environment release rehearsal succeeds
- [x] protected CI checks pass before every merge
- [x] the v0.16.0 tag, JAR, checksum, GitHub Release, and immutable publication record are verified

### Publication record

- release-finalization PR: `#187`
- reviewed release-candidate commit: `55694be7b76d122da10e52ddb1eab0de2fe48068`
- published merge and tag commit: `da8cefa9772d8e009b5ef1e5ab53d03bc44b1c13`
- annotated tag object: `8308e190960525924a550dafc8dcfcf61d4250d0`
- release workflow run: [`32757038003`](https://github.com/nursena-pc/payflow/actions/runs/32757038003)
- release workflow number: `9`
- published at: `2026-08-24T17:40:22Z`
- GitHub Release ID: `375880233`
- published JAR size: `100566879` bytes
- published JAR SHA-256: `8c542fc6928179345e5cda3d0f66d1481f7277a88096a52a69952ed95f2958e6`
- checksum asset SHA-256: `b14f5ea137012e7aa8557fa21c1c9fece151deb2447a0b636da7ee3a173d14b0`
- release checksum verification: passed
- published release notes verification: exact match

## v1.0.0 next stage

v1.0.0 release-candidate validation begins only after the complete v0.16.0
stabilization milestone and immutable publication record are complete.
