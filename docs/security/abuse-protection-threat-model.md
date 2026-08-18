# Generalized Abuse-Protection Threat Model

## Scope

This threat model covers the final v0.15.0 generalized abuse-protection design,
Redis enforcement, protected workflow wiring, observability, performance
evidence, and registration `DEFER` decision.

Generalized enforcement is wired for email-verification requests,
password-recovery requests, MFA login-challenge confirmation, and step-up grant
issuance. Registration remains a configured policy definition but is not wired.
Password login retains its existing separate Redis-backed limiter.

## Security objectives

- bound automated attempts by normalized/derived identity and effective client
- prevent untrusted forwarding headers from changing the client dimension
- preserve generic account-action responses and coarse MFA/step-up behavior
- make dependency-failure behavior deterministic for every wired workflow
- keep secrets, personal data, keys, counters, and TTL state outside observable
  output
- keep workflow, dimension, decision, reason, and failure labels finite
- retain reproducible evidence without treating developer-workstation results as
  production capacity certification

## Protected assets

- account existence and eligibility
- email-verification and password-recovery delivery capacity
- passwords, TOTP proofs, recovery codes, challenge tokens, and step-up grants
- normalized email addresses and effective client addresses
- Redis key names, digests, counts, expiration, and remaining quota
- application availability and operator confidence in metrics/evidence

## Attacker capabilities

An unauthenticated attacker can submit concurrent requests, rotate supplied
identity values, reuse one client, distribute traffic across clients, send
malformed or duplicated forwarding headers, measure coarse responses, and time
requests. An authenticated attacker can submit invalid MFA proofs or request
step-up grants repeatedly.

An attacker cannot configure trusted proxy CIDRs, read server-side digests, or
directly choose the resolved effective client address.

## Trust boundaries

1. The public HTTP boundary accepts attacker-controlled payloads and headers.
2. `ClientAddressResolver` accepts forwarding data only when the direct peer is
   a configured trusted proxy and otherwise uses the direct peer.
3. The owning user/MFA workflow normalizes or derives identity material before
   policy evaluation.
4. Application-facing policy contains only bounded workflow/policy types and has
   no servlet or Redis dependency.
5. The Redis adapter digests sensitive dimensions and owns atomic, expiring
   counter state without exposing raw inputs.
6. Logs, metrics, traces, errors, audits, dashboards, alerts, and committed
   evidence are disclosure boundaries and may contain only bounded safe data.

## Workflow policy and enforcement status

| Workflow | Identity dimension | Client dimension | Dependency mode | Public behavior | v0.15.0 enforcement |
|---|---|---|---|---|---|
| Registration | normalized proposed email | trusted effective address | configured fail closed | existing `201` / `400` / `409` | Not wired (`DEFER`) |
| Email-verification request | normalized email | trusted effective address | fail closed | generic empty `202`; no protected side effect | Wired |
| Password-recovery request | normalized email | trusted effective address | fail closed | generic empty `202`; no protected side effect | Wired |
| MFA challenge confirmation | fixed-length non-reversible challenge identifier | trusted effective address | fail closed | coarse unauthorized policy rejection; coarse unavailable dependency failure | Wired |
| Step-up grant issuance | authenticated subject | trusted effective address | fail closed | existing coarse step-up rejection; coarse unavailable dependency failure | Wired |

Configuration presence does not imply active enforcement. The registration row
exists so a future activation can reuse the same typed policy boundary after new
evidence/review.

## Threats and controls

### Forwarding-header spoofing

An attacker supplies `Forwarded` or `X-Forwarded-For` to rotate the client
quota. Wired workflows reuse the trusted-proxy resolver. Untrusted peers,
malformed chains, excessive hops, and oversized headers fall back to the direct
peer according to ADR 0011.

### Identity spraying and quota evasion

An attacker rotates identities or clients to avoid one dimension. Wired
workflows require both dimensions with workflow-specific limits. Neither raw
value becomes a Redis key; the adapter uses domain-separated fixed-length
digests and bounded key prefixes.

Distributed identities/clients can still consume resources within configured
bounds. That residual risk is accepted for v0.15.0 and is not represented as
eliminated by the workstation evidence.

### Account enumeration and coarse-response probing

Different quota or dependency outcomes can disclose account existence or
internal state. Email-verification and password-recovery requests therefore
retain the same empty `202` status/body for absent, ineligible, limited, and
fail-closed dependency outcomes.

MFA challenge quota rejection uses the same coarse unauthorized contract as
invalid challenge/proof outcomes. Step-up quota rejection reuses the existing
coarse failure boundary. Observable labels never include identity or
eligibility.

### Dependency-failure bypass

Timeouts, connection failures, malformed Redis results, and partial operations
must not silently permit protected work. Wired workflows are fail closed by
default. Fail-open behavior is visible in typed policy/telemetry but requires a
separate explicit security decision.

For account-action request endpoints, fail closed suppresses protected work while
preserving empty `202`. For MFA challenge/step-up, dependency failure maps to
the existing coarse `MFA_SECURITY_UNAVAILABLE` boundary.

### Counter persistence and cardinality

Missing expiration can create durable personal-data-derived state, while
attacker-selected metric labels can exhaust monitoring systems. Configuration
bounds windows/limits. One atomic Redis operation creates or repairs expiration
and limits key shape to finite workflow/dimension prefixes plus fixed-length
domain-separated digests.

### Credential disclosure and identity privacy

Passwords, account-action credentials, MFA proofs, recovery codes, login
challenges, step-up grants, email addresses, user identifiers, raw client
addresses, digests, Redis keys, counts, TTLs, and raw exception detail are
prohibited from logs, metrics, traces, errors, audits, dashboards, alerts, and
committed performance evidence.

### Registration resource exhaustion

Registration performs BCrypt hashing, persistence, uniqueness handling,
verification preparation, and mail enqueue work. Increment 6 measured the
complete successful path with disposable synthetic identities.

The bounded experiment observed no saturation, unexpected failure, health
failure, or dropped iteration through 16 registrations/second. That result did
not establish the material resource-exhaustion prerequisite for generalized
registration protection, so the reviewed v0.15.0 decision is `DEFER`.

No generalized registration limiter is wired and the existing `201` / `400` /
`409` contract remains unchanged. The experiment does not prove absence of risk
above the tested ceiling and does not certify production capacity. Future
`ACTIVATE` work requires new evidence and separate implementation/review.

### Configuration mistakes

Missing workflow policy, sub-second or greater-than-one-day windows,
non-positive limits, limits above one million, and absent failure modes fail
application startup. `ABUSE_PROTECTION_ENABLED` defaults off so generalized
activation is explicit.

Registration's configured policy must not be mistaken for endpoint wiring.
Operations/documentation must keep the `DEFER` boundary visible.

## Delivered workflow decisions

### Account-action requests

Email-verification and password-recovery policy runs before account lookup,
eligibility inspection, credential creation, and mail-outbox work. Every
allowed, blocked, dependency-failed, unknown, closed, verified, or otherwise
ineligible request retains the same empty `202` response.

Real-Redis HTTP/concurrency tests prove accepted response volume cannot increase
protected side effects beyond configured identity/client bounds.

### MFA challenge confirmation

MFA login-challenge confirmation derives a fixed-length non-reversible identity
before generalized enforcement and evaluates policy before challenge lookup,
row locking, attempt mutation, recovery-code consumption, challenge
consumption, or credential issuance. Malformed challenge input enters the same
enforcement boundary without exposing plaintext challenge material.

Quota rejection preserves the coarse unauthorized contract; Redis dependency
failure preserves the coarse `MFA_SECURITY_UNAVAILABLE` boundary.

### Step-up grant issuance

Step-up issuance uses authenticated JWT subject as the identity dimension and
trusted effective client as the client dimension. Enforcement runs before user
or authenticator locking, TOTP/recovery-code consumption, and grant creation or
supersession. Purpose parsing remains application-owned.

Real-Redis HTTP/concurrency tests prove configured identity/client limits bound
protected side effects, and untrusted forwarding headers cannot rotate the
client quota.

## Observability and operations

Generalized Micrometer metrics use only bounded workflow/outcome/reason and
workflow/failure-mode dimensions. The dedicated Grafana dashboard and
Prometheus rules operate on those bounded values.

Operators investigate through aggregate telemetry, service/Redis health,
deployment configuration, and trusted-client configuration. They must not
disable protection or switch fail-closed workflows to fail open as an incident
workaround.

## Performance evidence boundary

The accepted load harness uses pinned external tooling and keeps load execution
outside normal Maven verification. Evidence records environment, dataset,
warm-up, duration, arrival-rate model, request mix, latency percentiles,
throughput, expected policy outcomes, unexpected failures, dropped iterations,
saturation/overload observations, recovery, and limitations.

Quota-pressure evidence recorded zero bypass. Registration evidence supports
`DEFER`. These results are developer-workstation evidence and are not production
capacity certification or SLO evidence.

## Verification obligations

- retain unit coverage for policy bounds and complete configuration
- retain source/package boundary checks that keep application policy free of
  Spring/servlet/Redis imports
- retain real-Redis atomic expiration, threshold, dependency-failure, and
  concurrency tests
- retain HTTP contracts for generic/coarse public behavior under quota and
  dependency failure
- retain redaction/privacy checks across logs, metrics, traces, errors, audits,
  dashboards, alerts, and committed evidence
- retain the existing login-rate-limit contract suite unchanged
- retain executable Postman/OpenAPI/documentation drift contracts through
  release finalization

## Residual risks

Distributed clients and identity rotation can still consume resources within
configured bounds. Fixed windows can permit boundary bursts. Registration risk
above the measured 16 registrations/second ceiling remains unknown.

CAPTCHA, external bot intelligence, adaptive risk scoring, edge enforcement,
and production capacity certification remain explicit non-goals.
