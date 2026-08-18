# Generalized Abuse-Protection Policy

## Current delivery state

The v0.15.0 implementation uses the shared Redis enforcement foundation for
email-verification requests, password-recovery requests, MFA login-challenge
confirmation, and step-up grant issuance. Account-action requests evaluate
normalized email and the trusted effective client before account lookup. MFA
challenge confirmation evaluates a fixed-length, non-reversible challenge
identifier and the trusted effective client before challenge lookup or
sensitive state mutation. Step-up issuance evaluates the authenticated JWT
subject and trusted effective client before user/authenticator locking,
second-factor consumption, or grant creation.

Bounded Micrometer decisions, Redis-failure metrics, a dedicated Grafana
dashboard, Prometheus alerts, an operations runbook, and reviewed performance
evidence cover these workflows without changing enforcement semantics.
`ABUSE_PROTECTION_ENABLED` remains `false` by default so activation is an
explicit deployment decision. The existing login limiter remains a separate,
unchanged compatibility contract.

The registration workflow remains present in typed policy/configuration but is
not wired into generalized enforcement. Increment 6 produced an evidence-backed
`DEFER` decision because the bounded experiment did not demonstrate material
resource exhaustion through the tested 16 registrations/second ceiling. The
existing `201` / `400` / `409` registration contract is unchanged.
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

| Workflow | Window | Identity limit | Client limit | Failure mode | v0.15.0 wiring |
|---|---:|---:|---:|---|---|
| Registration | 15 minutes | 5 | 20 | `FAIL_CLOSED` | Not wired (`DEFER`) |
| Email-verification request | 15 minutes | 3 | 20 | `FAIL_CLOSED` | Wired |
| Password-recovery request | 15 minutes | 3 | 20 | `FAIL_CLOSED` | Wired |
| MFA login-challenge confirmation | 5 minutes | 5 | 20 | `FAIL_CLOSED` | Wired |
| Step-up grant issuance | 5 minutes | 5 | 20 | `FAIL_CLOSED` | Wired |

Environment variables use the `ABUSE_PROTECTION_` prefix. Each workflow exposes
`ENABLED`, `WINDOW`, `IDENTITY_LIMIT`, `CLIENT_LIMIT`, and `FAILURE_MODE`
settings. The global switch is `ABUSE_PROTECTION_ENABLED`.

Configuration presence does not imply endpoint wiring. In particular,
registration keeps its validated policy definition for a possible future
review, but the v0.15.0 application path does not invoke generalized
registration enforcement.

Changing a failure mode to `FAIL_OPEN` requires a security review, updated ADR
and threat-model evidence, public-contract tests, and explicit operational
approval. It must never be used as an automatic response to Redis instability.
## Account-action HTTP contract

- email-verification and password-recovery requests evaluate policy before any
  account lookup or row lock
- normalized email is the identity dimension
- only `ClientAddressResolver` supplies the effective client dimension
- allowed, limited, unknown, closed, verified, eligible, and otherwise
  ineligible outcomes retain an empty `202 Accepted` response
- blocked and fail-closed outcomes create no credential or mail-outbox work
- concurrent requests cannot create more protected side effects than the
  configured identity or client limit

Registration remains configured but is not wired under the reviewed Increment 6
evidence-backed `DEFER` decision. The bounded experiment did not demonstrate
material resource exhaustion through 16 registrations/second, so v0.15.0 adds
no generalized registration limiter and preserves the existing `201` / `400` /
`409` public contract. The experiment is developer-workstation evidence only
and does not prove absence of risk above the tested ceiling. A future
`ACTIVATE` decision requires new evidence and a separately reviewed
implementation/comparison checkpoint.
## MFA and step-up HTTP contract

- MFA challenge confirmation derives its identity quota from a fixed-length,
  non-reversible challenge identifier rather than plaintext challenge material
- malformed challenge input participates in the same fail-closed enforcement
  boundary before any challenge repository access
- challenge quota rejection does not decrement attempts, consume recovery
  codes, consume challenge state, or issue access/refresh credentials
- step-up grant issuance uses only the authenticated JWT subject as its identity
  dimension; account identity is never accepted from the request body
- step-up quota rejection runs before user/authenticator locking, second-factor
  consumption, and grant creation or supersession
- only `ClientAddressResolver` supplies the effective client dimension for both
  workflows, so untrusted forwarding headers cannot rotate client quota
- Redis dependency failure remains fail closed and maps to the existing coarse
  `MFA_SECURITY_UNAVAILABLE` public contract without sensitive mutation

## Privacy and observability

Generalized enforcement emits only finite application-owned classifications.
`payflow.security.abuse_protection.decisions` uses bounded `workflow`,
`outcome`, and `reason` tags. `payflow.security.abuse_protection.redis.failures`
uses bounded `workflow` and `failure_mode` tags.

Email addresses, user identifiers, JWT subjects, raw client addresses,
credentials, proofs, tokens, digests, Redis keys, counters, TTL values, request
URIs, and raw exception classes are prohibited from metric labels, dashboard
dimensions, alert annotations, and incident notes.

See the [Abuse-Protection Operations
Runbook](operations/abuse-protection-observability.md) for safe triage,
mitigation, rollback, and false-positive handling.

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
