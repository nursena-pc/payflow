# Generalized Abuse-Protection Threat Model

## Scope

This threat model covers the v0.15.0 policy foundation for registration,
email-verification requests, password-recovery requests, MFA login-challenge
confirmation, and step-up grant issuance. Password login retains its existing
rate limiter. Redis enforcement and endpoint wiring are delivered separately.

## Security objectives

- bound automated attempts by normalized identity and effective client
- prevent untrusted forwarding headers from changing the client dimension
- preserve generic account-action responses and anti-enumeration behavior
- make dependency-failure behavior deterministic for every workflow
- keep secrets, personal data, keys, and counters outside observable output
- keep workflow, dimension, decision, and failure labels finite

## Protected assets

- account existence and eligibility
- email-verification and password-recovery delivery capacity
- passwords, TOTP proofs, recovery codes, challenge tokens, and step-up grants
- normalized email addresses and effective client addresses
- Redis key names, digests, counts, expiration, and remaining quota
- application availability and operator confidence in metrics

## Attacker capabilities

An unauthenticated attacker can submit concurrent requests, rotate supplied
identity values, reuse one client, distribute traffic across clients, send
malformed or duplicated forwarding headers, measure coarse responses, and time
requests. An authenticated attacker can submit invalid MFA proofs or request
step-up grants repeatedly. An attacker cannot configure trusted proxy CIDRs,
read server-side digests, or directly choose the resolved effective address.

## Trust boundaries

1. The public HTTP boundary accepts attacker-controlled payloads and headers.
2. `ClientAddressResolver` accepts forwarding data only when the direct peer is
   a configured trusted proxy and otherwise uses the direct peer.
3. The owning user or MFA workflow normalizes identity material before policy
   evaluation.
4. The application-facing policy contains only bounded workflow and policy
   types; it has no servlet or Redis dependency.
5. The Increment 2 Redis adapter digests sensitive dimensions and owns atomic,
   expiring counter state without exposing raw inputs.
6. Logs, metrics, traces, errors, and audits are disclosure boundaries and may
   contain only bounded coarse classifications.

## Workflow policy

| Workflow | Identity dimension | Client dimension | Dependency mode | Public behavior |
|---|---|---|---|---|
| Registration | normalized proposed email | trusted effective address | fail closed | stable coarse rejection |
| Email-verification request | normalized email | trusted effective address | fail closed | generic accepted response; no side effect |
| Password-recovery request | normalized email | trusted effective address | fail closed | generic accepted response; no side effect |
| MFA challenge confirmation | fixed-length non-reversible challenge identifier | trusted effective address | fail closed | stable coarse rejection |
| Step-up grant issuance | authenticated subject | trusted effective address | fail closed | stable coarse rejection |

The table defines policy intent, not active endpoint enforcement in Increment 1.

## Threats and controls

### Forwarding-header spoofing

An attacker supplies `Forwarded` or `X-Forwarded-For` to rotate the client
quota. The application must reuse the trusted-proxy resolver. Untrusted peers,
malformed chains, excessive hops, and oversized headers fall back to the direct
peer according to ADR 0011.

### Identity spraying and quota evasion

An attacker rotates identities or clients to avoid one dimension. Selected
workflows require both dimensions with endpoint-specific limits. Neither raw
value may become a Redis key; a later adapter must use domain-separated,
fixed-length digests and bounded key prefixes.

### Account enumeration

Different quota or dependency outcomes can disclose whether an account exists.
Email-verification and password-recovery request endpoints retain the same
generic accepted status and body whether the identity is absent, ineligible,
limited, or blocked by a dependency failure. Observable labels never include
identity or eligibility.

### Dependency-failure bypass

Timeouts, connection failures, malformed Redis results, and partial operations
must not silently permit protected work. Each configured workflow has an
explicit failure mode. The initial policy set is fail closed. Later adapters
must map failures to the workflow-specific public behavior above.

### Counter persistence and cardinality

Missing expiration can create durable personal-data-derived state, while
attacker-selected metric labels can exhaust monitoring systems. Configuration
bounds windows and limits. Increment 2 uses one atomic operation, creates or
repairs explicit expiration, and limits keys to finite workflow and dimension
prefixes plus fixed-length domain-separated digests.

### Credential disclosure

Passwords, account-action tokens, MFA proofs, recovery codes, login challenges,
step-up grants, email addresses, client addresses, digests, Redis keys, counts,
and TTLs are prohibited from logs, metrics, traces, errors, and audits. Tests
must inspect success, rejection, and dependency-failure paths.

### Configuration mistakes

Missing workflow policy, sub-second or greater-than-one-day windows, non-positive
limits, limits above one million, and absent failure modes fail application
startup. The global policy switch defaults off until enforcement is wired.

## Increment 3 account-action decision

Email-verification and password-recovery request policy runs before account
lookup, eligibility inspection, credential creation, and mail-outbox work.
Every allowed, blocked, dependency-failed, unknown, closed, verified, or
otherwise ineligible request retains the same empty `202` public response.
Real-Redis HTTP concurrency tests prove that accepted response volume cannot
increase protected side effects beyond the configured bound.

Registration protection remains deferred pending Increment 6 evidence. Unlike
the two generic account-action request endpoints, registration already has
distinct `201` and duplicate-account `409` behavior and performs BCrypt plus
initial verification preparation. Latency, overload, and false-positive data
must be recorded before choosing its public failure contract and activation
policy.

## Increment 4 MFA and step-up decision

MFA login-challenge confirmation derives a fixed-length non-reversible identity
before generalized enforcement and evaluates policy before challenge lookup,
row locking, attempt mutation, recovery-code consumption, challenge
consumption, or credential issuance. Malformed challenge input also enters the
enforcement boundary without exposing plaintext challenge material.

Step-up grant issuance uses the authenticated JWT subject as its identity
dimension and the trusted effective client as its client dimension. Enforcement
runs before user or authenticator locking, TOTP or recovery-code consumption,
and grant creation or supersession. Purpose parsing remains application-owned.

Real-Redis HTTP and concurrency tests prove configured identity and client
limits bound protected side effects. Untrusted forwarding headers cannot change
the client quota. Redis-unavailable tests prove both workflows fail closed with
the coarse `MFA_SECURITY_UNAVAILABLE` contract while challenge attempts,
recovery codes, credentials, and grants remain untouched.

## Verification obligations

- unit-test policy bounds and complete workflow configuration
- prove application policy source contains no Spring, servlet, HTTP, or Redis
  imports
- test trusted-client integration during Increment 2
- test atomic expiration and concurrency with real Redis during Increment 2
- verify generic public behavior during endpoint increments
- scan logs, metrics, traces, errors, and audits for prohibited material
- retain the existing login-rate-limit contract suite unchanged

## Residual risks

Distributed clients and identity rotation can still consume resources within
configured bounds. Fixed windows can permit boundary bursts. These risks are
accepted for v0.15.0 and must be measured by the load and performance increment.
CAPTCHA, external bot intelligence, adaptive risk scoring, and edge enforcement
remain explicit non-goals.
