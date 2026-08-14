# ADR 0015: Establish a generalized abuse-protection policy boundary

- Status: Accepted
- Date: 2026-08-14
- Tracking issue: [#151](https://github.com/nursena-pc/payflow/issues/151)
- Milestone: [#149](https://github.com/nursena-pc/payflow/issues/149)

## Context

PayFlow already protects password login with a Redis-backed fixed-window
limiter and resolves an effective client address through a trusted-proxy
boundary. Email-verification requests, password-recovery requests,
registration, MFA login-challenge confirmation, and step-up grant issuance
need endpoint-specific protection without copying login-specific Redis,
controller, or servlet behavior.

The policy must preserve anti-enumeration responses and must not expose email
addresses, client addresses, credentials, proofs, Redis keys, or counter state.
The first increment froze policy and configuration. Increment 2 adds a separate
atomic Redis enforcement adapter while endpoint wiring remains deferred.

## Decision

PayFlow introduces a package-bounded `abuseprotection` capability. Its
application policy has no Spring, servlet, HTTP, controller, or Redis
dependency.

`AbuseProtectionWorkflow` is a closed enum with five bounded identifiers:

- `registration`
- `email-verification-request`
- `password-recovery-request`
- `mfa-login-challenge-confirmation`
- `step-up-grant-issuance`

`AbuseProtectionPolicy` defines whether a workflow is enabled, its fixed
window, positive per-identity and per-client limits, and its deterministic
dependency-failure mode. Windows are bounded from one second through one day.
Each limit is bounded from one through one million. These bounds prevent
invalid duration, non-expiring state, and accidental unbounded configuration.

`AbuseProtectionPolicyProvider` is the application-facing lookup boundary.
Configuration is supplied by an outbound configuration adapter under
`payflow.security.abuse-protection`. The global switch defaults to `false`
until Redis enforcement and protected workflow wiring are delivered.

## Failure policy

Every initially selected workflow is configured `FAIL_CLOSED`. The meaning of
fail-closed is workflow-specific and must retain its established public
contract:

- email-verification and password-recovery request endpoints keep their generic
  accepted response while suppressing the protected side effect
- registration, MFA challenge confirmation, and step-up issuance reject the
  operation through a stable coarse dependency-unavailable response
- no fallback bypasses client or identity policy by trusting forwarding headers

`FAIL_OPEN` remains a typed policy value for an explicitly reviewed future
workflow. It is never selected implicitly after a timeout, malformed result,
or partial Redis failure.

## Identity and client boundaries

The policy does not accept servlet requests or forwarding headers. A later
inbound adapter must reuse `ClientAddressResolver` and its validated direct-peer
fallback. Identity material must be normalized by the owning application
workflow and transformed into a fixed-length digest before any Redis key is
created. Raw identity and client material must never become a metric label,
log field, trace attribute, error detail, or audit payload.

## Compatibility

The existing `LoginRateLimitPort`, configuration prefix, public errors,
metrics, Redis script, and reset behavior remain unchanged. Migration or reuse
of login enforcement requires separate evidence and is not part of this
decision.

## Consequences

- endpoint policy becomes explicit and testable before enforcement is added
- configuration fails startup when a selected policy is absent or invalid
- bounded workflow enums can safely support future low-cardinality metrics
- Redis implementation and HTTP behavior remain independently replaceable
- policy defaults cannot be mistaken for active enforcement because the global
  switch remains disabled in this increment

## Rejected alternatives

- controller annotations: couple policy to HTTP adapters and obscure failure
  behavior
- one global quota: cannot represent workflow-specific risk and response
  contracts
- accepting raw forwarding headers: permits attacker-controlled quota bypass
- reusing the login port directly: leaks login-specific identity and reset
  semantics into unrelated workflows
- silent fail-open behavior: turns Redis failure into an abuse-control bypass

## Non-goals

Increment 2 implements Redis counters but does not implement endpoint
interception, public error changes, dashboards, alerts, load tests, CAPTCHA,
third-party bot detection, WAF or API-gateway deployment, adaptive scoring, or
active-authenticator replacement.
