# Generalized Abuse-Protection Policy

## Current delivery state

Increment 3 wires the shared Redis foundation into email-verification and
password-recovery request workflows. Both evaluate normalized identity and the
trusted effective client address before account lookup. Allowed requests retain
their existing eligibility rules; blocked and fail-closed requests suppress
credential and mail-outbox side effects while returning the same empty `202`
response. `ABUSE_PROTECTION_ENABLED` remains `false` by default so activation
is an explicit deployment decision. The login limiter remains unchanged.

## Policy contract

The application-facing contract consists of:

- `AbuseProtectionWorkflow`: five stable, bounded workflow identifiers
- `AbuseProtectionFailureMode`: explicit `FAIL_CLOSED` or `FAIL_OPEN`
- `AbuseProtectionPolicy`: enabled state, window, identity limit, client limit,
  and failure behavior
- `AbuseProtectionPolicyProvider`: adapter-independent lookup by workflow

Policy code contains no controller, servlet, HTTP, Spring, or Redis dependency.

## Validation

- every workflow configuration is required
- windows range from one second through one day
- identity and client limits range from one through one million
- every workflow declares a dependency-failure mode
- the global switch can disable enforcement without discarding validated policy

Invalid configuration fails application startup.

## Configuration

| Workflow | Window | Identity limit | Client limit | Failure mode |
|---|---:|---:|---:|---|
| Registration | 15 minutes | 5 | 20 | `FAIL_CLOSED` |
| Email-verification request | 15 minutes | 3 | 20 | `FAIL_CLOSED` |
| Password-recovery request | 15 minutes | 3 | 20 | `FAIL_CLOSED` |
| MFA login-challenge confirmation | 5 minutes | 5 | 20 | `FAIL_CLOSED` |
| Step-up grant issuance | 5 minutes | 5 | 20 | `FAIL_CLOSED` |

Environment variables use the `ABUSE_PROTECTION_` prefix. Each workflow exposes
`ENABLED`, `WINDOW`, `IDENTITY_LIMIT`, `CLIENT_LIMIT`, and `FAILURE_MODE`
settings. The global switch is `ABUSE_PROTECTION_ENABLED`.

Changing a failure mode to `FAIL_OPEN` requires a security review, updated ADR
and threat-model evidence, public-contract tests, and explicit operational
approval. It must never be used as an automatic response to Redis instability.

## Account-action HTTP contract

- email-verification and password-recovery requests evaluate policy before any
  account lookup or row lock
- normalized email is the identity dimension
- only `ClientAddressResolver` supplies the effective client dimension
- allowed, limited, unknown, closed, verified, and otherwise ineligible
  outcomes retain an empty `202 Accepted` response
- blocked and fail-closed outcomes create no credential or mail-outbox work
- concurrent requests cannot create more protected side effects than the
  configured identity or client limit

Registration was evaluated but is not wired in Increment 3. Its existing
`201`/`409` contract, BCrypt cost, and initial verification-mail side effect
require the reproducible performance and overload evidence planned for
Increment 6 before an activation decision. This is a documented deferral, not
an implicit exemption from later review.

## Privacy and observability

Future enforcement may emit only finite workflow, dimension, decision, and
failure classifications. Email addresses, raw client addresses, credentials,
proofs, tokens, digests, Redis keys, counts, and TTL values are prohibited from
observable output.

## Compatibility

`payflow.security.login-rate-limit` and the corresponding
`LOGIN_RATE_LIMIT_*` variables remain unchanged. Increment 2 adds a separate
adapter and script; it does not replace, adapt, disable, or reset the login
limiter.

## Redis state contract

- one Lua invocation increments identity and client counters atomically
- every new key receives expiration and a missing expiration is repaired
- key prefixes contain only bounded workflow and dimension identifiers
- key suffixes are domain-separated 64-character SHA-256 digests
- values contain counters only
- malformed results, timeouts, and connection failures follow the workflow's
  explicit dependency-failure mode
- `FAIL_OPEN` permits work only when selected explicitly; `FAIL_CLOSED` raises a
  coarse dependency-unavailable outcome without sensitive detail

See [ADR 0015](adr/0015-generalized-abuse-protection.md), the
[threat model](security/abuse-protection-threat-model.md),
[ADR 0010](adr/0010-redis-login-rate-limiting.md), and
[ADR 0011](adr/0011-trusted-client-context.md).
