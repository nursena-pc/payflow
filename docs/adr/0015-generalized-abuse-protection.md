# ADR 0015: Establish a generalized abuse-protection policy boundary

- Status: Accepted
- Date: 2026-08-14
- Last aligned: 2026-08-18
- Tracking issue: [#151](https://github.com/nursena-pc/payflow/issues/151)
- Milestone: [#149](https://github.com/nursena-pc/payflow/issues/149)
- Release finalization: [#166](https://github.com/nursena-pc/payflow/issues/166)

## Context

PayFlow already protected password login with a Redis-backed fixed-window
limiter and resolved effective client addresses through a trusted-proxy
boundary. v0.15.0 needed endpoint-specific abuse protection for
email-verification requests, password-recovery requests, MFA login-challenge
confirmation, and step-up grant issuance without copying login-specific Redis,
controller, or servlet behavior.

Registration was included in the typed policy vocabulary because it performs
BCrypt work, persistence, uniqueness checks, verification preparation, and mail
side effects, but its activation required measured evidence rather than
assumption.

The design must preserve anti-enumeration/coarse public responses, keep raw
identity and credential material outside Redis key names and observable output,
and make dependency failure deterministic.

## Decision

PayFlow uses a package-bounded `abuseprotection` capability. The application policy
has no Spring, servlet, HTTP, controller, or Redis dependency.

`AbuseProtectionWorkflow` is a closed enum with five bounded identifiers:

- `registration`
- `email-verification-request`
- `password-recovery-request`
- `mfa-login-challenge-confirmation`
- `step-up-grant-issuance`

`AbuseProtectionPolicy` defines enabled state, fixed window, positive
per-identity and per-client limits, and deterministic dependency-failure mode.
Windows are bounded from one second through one day. Limits are bounded from one
through one million. `AbuseProtectionPolicyProvider` is the application-facing
lookup boundary; configuration is supplied under
`payflow.security.abuse-protection`.

The global switch defaults to `false` through `ABUSE_PROTECTION_ENABLED` even
after implementation is complete. Enabling generalized protection is an
explicit deployment choice, not a migration side effect or incident-response
toggle.

## v0.15.0 workflow status

| Workflow | Identity dimension | Client dimension | Failure mode | v0.15.0 status |
|---|---|---|---|---|
| Registration | normalized proposed email | trusted effective address | configured `FAIL_CLOSED` | Not wired (`DEFER`) |
| Email-verification request | normalized email | trusted effective address | `FAIL_CLOSED` | Wired |
| Password-recovery request | normalized email | trusted effective address | `FAIL_CLOSED` | Wired |
| MFA login-challenge confirmation | fixed-length non-reversible challenge identifier | trusted effective address | `FAIL_CLOSED` | Wired |
| Step-up grant issuance | authenticated subject | trusted effective address | `FAIL_CLOSED` | Wired |

Configuration presence does not imply endpoint wiring. Registration keeps a
validated policy definition for future review, but v0.15.0 does not invoke that
policy from the registration path.

## Failure policy

The wired v0.15.0 workflows are fail closed while preserving established public
contracts:

- email-verification and password-recovery request endpoints keep the generic
  accepted response while suppressing the protected side effect; concretely,
  all coarse outcomes share the empty `202 Accepted` response
- MFA challenge quota rejection keeps the coarse unauthorized contract
- MFA/step-up Redis dependency failure maps to the existing coarse
  `MFA_SECURITY_UNAVAILABLE` contract
- step-up policy rejection reuses the established coarse step-up failure
  boundary
- no fallback trusts attacker-controlled forwarding headers

`FAIL_OPEN` remains a typed value only for an explicitly reviewed future policy.
It is never selected implicitly after timeout, malformed Redis result, or
partial dependency failure.

Because registration is not wired, the configured registration failure mode is
not part of the v0.15.0 public runtime contract. Registration continues to use
its existing `201` / `400` / `409` responses.

## Identity and client boundaries

Controllers do not own limiter policy. Public/account-security adapters resolve
the effective client only through `ClientAddressResolver`. Owning application
workflows normalize or derive the identity dimension before generalized policy
evaluation.

The Redis adapter transforms sensitive dimensions into domain-separated,
fixed-length SHA-256 digests. Raw email, subject, challenge, or client values do
not become Redis key suffixes, metric labels, log fields, trace attributes,
error details, or audit payloads.

## Redis state and concurrency

One Lua operation evaluates identity and client quotas atomically. New keys
receive expiration and missing expiration is repaired. Keys contain only bounded
workflow/dimension prefixes plus fixed-length digests; values contain counters
only.

Real-Redis tests cover threshold, expiration, combined decisions, dependency
failure, and concurrency. Protected side effects cannot exceed the applicable
quota under concurrent request pressure.

## Privacy and observability

Micrometer records only bounded application-owned workflow, outcome, reason, and
failure-mode dimensions. The dedicated Grafana dashboard, Prometheus alerts, and
operations runbook use those same bounded classifications.

Email addresses, user identifiers, JWT subjects, challenge tokens/digests, TOTP
values, recovery codes, step-up grants, raw client addresses, Redis keys,
counters, TTL values, and raw exception classes remain prohibited from
observable output and incident notes.

## Compatibility

The existing `LoginRateLimitPort`, configuration prefix, public errors,
metrics, Redis script, reset behavior, and Postman verification workflow remain
separate and unchanged. Generalized abuse protection does not replace or reset
password-login limiting.

## Evidence-backed registration decision

Increment 6 measured the complete successful registration path with disposable
synthetic identities on a developer workstation. No saturation, unexpected
failure, health failure, or dropped iteration was observed through the tested
16 registrations/second ceiling, and recovery remained within the frozen
budget.

The reviewed decision is `DEFER`. The experiment did not demonstrate the
material resource-exhaustion prerequisite required to justify an `ACTIVATE`
implementation/comparison checkpoint. v0.15.0 therefore adds no generalized
registration limiter.

This evidence is environment-specific and is not production capacity
certification. It does not prove absence of risk above the tested ceiling. A
future activation requires new evidence, a separately reviewed implementation,
preservation of the existing public registration contract unless explicitly
versioned otherwise, and the required protected-versus-unprotected regression
comparison.

## Consequences

- generalized policy remains explicit and testable outside HTTP/Redis adapters
- four sensitive identity/security workflows share one bounded Redis
  enforcement foundation without sharing public response semantics
- anti-enumeration and coarse-response boundaries remain workflow-owned
- operational telemetry is useful without exposing identity or credential data
- deployment activation remains explicit
- registration remains a documented, evidence-backed non-wiring decision rather
  than an accidental exemption
- developer-workstation load evidence is retained for reproducibility without
  being promoted to a production SLO or capacity claim

## Rejected alternatives

- controller annotations: couple policy to HTTP adapters and obscure failure
  behavior
- one global quota: cannot represent workflow-specific risk and response
  contracts
- accepting raw forwarding headers: permits attacker-controlled quota bypass
- reusing the login port directly: leaks login-specific identity/reset
  semantics into unrelated workflows
- silent fail-open behavior: turns Redis failure into an abuse-control bypass
- automatic registration activation from configured policy values: bypasses
  the required evidence and review boundary
- retuning benchmark thresholds to justify activation: invalidates the frozen
  measurement contract

## Non-goals

- active-authenticator replacement
- CAPTCHA or third-party bot detection
- CDN, WAF, API-gateway, or Kubernetes deployment
- adaptive machine-learning risk scoring
- frontend implementation
- production capacity certification from developer-workstation evidence
- changing the existing password-login limiter
