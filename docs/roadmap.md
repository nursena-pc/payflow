# Delivery Roadmap

## Current delivery focus

PayFlow v0.10.0 is the latest tagged release. The active development line uses
`0.11.0-SNAPSHOT`.

The v0.10.0 trusted client-context increment is released and independently
verified. The current focus is post-release stabilization and selection of the
next v0.11.0 increment through a dedicated issue with explicit scope, threat
model, acceptance criteria, and rollback boundaries.

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

## Future candidates

Potential v0.11.0 increments include:

- structured JSON logging and request correlation
- signing-key rotation and external key-management integration
- load and performance verification
- password recovery and verified-email workflows
- multi-factor authentication
- broader operational-security dashboards
- generalized API abuse protection
