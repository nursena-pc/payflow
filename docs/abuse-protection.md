# Generalized Abuse-Protection Policy

## Current delivery state

Increment 1 defines and validates policy configuration. Generalized Redis
enforcement is not active yet: `ABUSE_PROTECTION_ENABLED` defaults to `false`.
The existing login limiter remains independently active and unchanged.

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

## Privacy and observability

Future enforcement may emit only finite workflow, dimension, decision, and
failure classifications. Email addresses, raw client addresses, credentials,
proofs, tokens, digests, Redis keys, counts, and TTL values are prohibited from
observable output.

## Compatibility

`payflow.security.login-rate-limit` and the corresponding
`LOGIN_RATE_LIMIT_*` variables remain unchanged. Increment 1 does not replace,
adapt, disable, or reset the login limiter.

See [ADR 0015](adr/0015-generalized-abuse-protection.md), the
[threat model](security/abuse-protection-threat-model.md),
[ADR 0010](adr/0010-redis-login-rate-limiting.md), and
[ADR 0011](adr/0011-trusted-client-context.md).
