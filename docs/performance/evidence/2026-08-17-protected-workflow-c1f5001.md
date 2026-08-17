# Accepted protected-workflow performance evidence

## Verdict

**ACCEPTED** against the frozen v0.15.0 Increment 6 developer-workstation
performance contract.

This record is a reviewed, bounded aggregate generated from ignored runtime
output. It is not a production SLO, capacity certification, or regulatory claim.

## Provenance

- Measured Git commit: `c1f5001b3709225d4865af60c621e2595cc58b59`
- Branch during measurement: `feat/v0.15.0-performance-evidence`
- Run identifier: `20260817T185447Z`
- Generated UTC: 2026-08-17T19:03:54.5158755Z
- Candidate JSON SHA-256: `36c0767aef6bd49db484788632f8bac1d7df111ed70e8a7160c6934bdddf1f88`
- Candidate Markdown SHA-256: `d418f795377f96ac6f0ccd02fab2b058567fbefbded5a57f0e60eba780dc8fe8`
- Source raw summaries: retained only under ignored `performance/results/`
- Promotion rule: this document records the measured commit above; the later
  documentation commit is not the measured application commit

## Environment

- Host OS: Microsoft Windows NT 10.0.26200.0
- PowerShell: 5.1.26100.9168
- Docker server: 29.6.1
- Docker environment: Docker Desktop
- Docker resources: 16 CPU; 7.61 GiB
- Java runtime: openjdk 21.0.11 2026-04-21 LTS
- k6 runtime: k6 v2.1.0 (commit/83a87a41e2, go1.26.4, linux/amd64)
- Compose files: `compose.yml`, `performance/k6/compose.yml`
- Compose profiles: `app`, `loadtest`
- Generalized abuse protection: enabled

## Scenario and method

- Representative workflow: `POST /api/v1/auth/email-verification/requests`
- Request model: k6 constant-arrival-rate protected-workflow executor plus a
  separate 1 request/second health probe
- Dataset: disposable synthetic identities under the reserved `example.invalid`
  domain; no runtime identity value is included in this record
- Setup cost: excluded from measured request latency
- Isolation: only the isolated Redis test state was reset before each
  independent phase; application and dependency containers otherwise remained
  unchanged
- Warm-up: 30 seconds at 5 iterations/second
- Steady state: 120 seconds at 10 iterations/second
- Saturation discovery: 60-second stages at 10, 20, 40, and 80
  iterations/second
- Overload rule: 120 iterations/second because no earlier saturation was
  observed
- Recovery budget: 30 seconds

## Measurements

| Phase | Target it/s | Achieved it/s | Achievement | Seconds | Requests | p50 ms | p95 ms | p99 ms | Unexpected | Health failures | Dropped | Allowed | Blocked client | Identity/Both/Bypass | Saturated |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| warmup | 5 | 5.033 | 100.667% | 30 | 151 | 6.518 | 10.182 | 196.250 | 0.000% | 0.000% | 0 | 20 | 131 | 0/0/0 | False |
| steady | 10 | 10.008 | 100.083% | 120 | 1201 | 4.090 | 7.252 | 9.600 | 0.000% | 0.000% | 0 | 20 | 1181 | 0/0/0 | False |
| saturation-10 | 10 | 10.017 | 100.167% | 60 | 601 | 3.521 | 5.269 | 6.126 | 0.000% | 0.000% | 0 | 20 | 581 | 0/0/0 | False |
| saturation-20 | 20 | 20.000 | 100.000% | 60 | 1200 | 2.818 | 4.409 | 5.780 | 0.000% | 0.000% | 0 | 20 | 1180 | 0/0/0 | False |
| saturation-40 | 40 | 40.000 | 100.000% | 60 | 2400 | 2.525 | 4.104 | 5.637 | 0.000% | 0.000% | 0 | 20 | 2380 | 0/0/0 | False |
| saturation-80 | 80 | 80.017 | 100.021% | 60 | 4801 | 1.840 | 2.824 | 4.076 | 0.000% | 0.000% | 0 | 20 | 4781 | 0/0/0 | False |
| overload | 120 | 120.017 | 100.014% | 60 | 7201 | 1.741 | 2.627 | 4.282 | 0.000% | 0.000% | 0 | 20 | 7181 | 0/0/0 | False |

## Frozen steady-state acceptance

| Gate | Budget | Result | Verdict |
|---|---:|---:|---|
| p95 request duration | <= 750 ms | 7.252 ms | PASS |
| p99 request duration | <= 1500 ms | 9.600 ms | PASS |
| unexpected failure rate | < 0.5% | 0.000% | PASS |
| dropped iterations | 0 | 0 | PASS |
| achieved target rate | >= 95% | 100.083% | PASS |
| health probe failure rate | 0% | 0.000% | PASS |

Steady-state verdict: **ACCEPTED**.

## Quota and security correctness

Every independently reset phase recorded exactly **20 allowed decisions**.
All remaining measured protected-workflow requests were expected client-policy
blocks while the public anti-enumerating request contract remained successful.

Across every phase:

- identity-only blocked decisions: **0**
- combined identity/client blocked decisions: **0**
- dependency-bypass decisions: **0**
- unexpected HTTP/transport failures: **0%**
- dropped iterations: **0**
- health-probe failures: **0%**

The run therefore preserved the reviewed client quota boundary without a
protection bypass under steady, saturation-discovery, or overload traffic.

## Saturation, overload, and recovery

First saturation: **not observed through 80 iterations/s**.

The frozen fallback overload stage therefore ran at **120 iterations/s**.
Overload at 120 iterations/s: **not saturated**. It completed
7201 protected-workflow requests with p95
2.627 ms, p99 4.282 ms, zero unexpected failures,
zero dropped iterations, 20 allowed decisions, and
7181 expected client-policy blocks.

No saturation point or limiting resource was observable at or below the tested
120 iterations/second ceiling in this developer-workstation environment. This
is a bounded observation, not a claim about production capacity.

Recovery: **0.012 seconds**, within the frozen 30-second budget.

## Limitations

- Developer-workstation evidence only; this is not production capacity
  certification.
- The representative account-action workflow becomes client-policy-limited
  after the reviewed twenty-decision boundary in every independently reset
  phase, so high-rate latency primarily characterizes the protected coarse
  response path after that boundary.
- Each phase resets only isolated Redis test state; the application and required
  dependency containers otherwise remain unchanged.
- Results are not directly comparable across materially different Docker, CPU,
  memory, Java, or k6 environments.
- No saturation point was reached at or below 120 iterations/second, so this run
  establishes only a tested lower bound for the environment rather than a
  saturation capacity.
- Raw k6 summaries remain ignored and are not committed with this aggregate.

## Privacy review

This committed record contains only reviewed aggregate counts, latency,
throughput, environment metadata, the measured Git commit, and bounded
methodology text. No reusable runtime identity, credential, authentication
material, raw client address, datastore key, counter value, or mail credential
is included.

## Follow-up boundary

This evidence accepts the already-protected representative workflow only.
It does **not** decide whether generalized abuse protection should be wired into
registration. The registration experiment and explicit `ACTIVATE` or `DEFER`
decision remain a separate reviewed checkpoint under issue #164.
