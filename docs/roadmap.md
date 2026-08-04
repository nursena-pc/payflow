# Delivery Roadmap

## Current delivery focus

PayFlow v0.11.0 is the latest tagged release. The v0.12.0 release candidate uses
the Maven version `0.12.0`.

The v0.11.0 observability release was published from verified merge commit
`00401d55546fb819fe7d96a8fad8e8c43e37649c`. The v0.12.0 JWT signing-key
rotation increment was merged through protected PR #113. The current focus is
protected release review, then replacement of the invalid pre-release tag only
after the verified release-preparation merge commit is known.

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

## v0.12.0 — Release Candidate: JWT Signing-Key Rotation

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
- [ ] Merge v0.12.0 release preparation through a protected pull request
- [ ] Tag the verified release merge commit as `v0.12.0`
- [ ] Publish the executable JAR, SHA-256 checksum, and GitHub Release

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
- [ ] v0.12.0 release preparation and publication gates complete

## Later v1.0 candidates

Later v1.0 candidates include verified-email and password-recovery workflows,
multi-factor authentication, generalized abuse protection, load/performance
evidence, backup/restore rehearsal, API freeze, SBOM generation, and release
stabilization.
