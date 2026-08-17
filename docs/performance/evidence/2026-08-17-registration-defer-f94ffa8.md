# Registration protection decision evidence

## Decision

**DEFER** generalized abuse-protection wiring for registration in v0.15.0.

The frozen Increment 6 rule requires evidence of a material registration
resource-exhaustion risk before an `ACTIVATE` implementation/comparison
checkpoint is justified. The bounded unprotected experiment did not demonstrate
that prerequisite risk.

No production registration protection wiring is added by this decision.

## Provenance

- Measured Git commit: `f94ffa8870439abb1e17d8ae46a1cf16abe8c572`
- Branch during measurement: `feat/v0.15.0-performance-evidence`
- Run identifier: `20260817T204657Z`
- Generated UTC: 2026-08-17T20:49:49.2382702Z
- Candidate JSON SHA-256: `60a8637c3503a84ebcb8a70df8fad94d05e45800f2de0b2095087945e1ba7356`
- Candidate Markdown SHA-256: `e9a0c289f6c2a3c87f381743248acc3ee9d057f6ad7e76ef852f119c670cc37d`
- Candidate files remain ignored under `performance/results/registration/`

## Environment

- Host OS: Microsoft Windows NT 10.0.26200.0
- Docker server: 29.6.1
- Docker environment: Docker Desktop
- Docker resources: 16 CPU; 7.61 GiB
- Java runtime: openjdk 21.0.11 2026-04-21 LTS
- k6 runtime: k6 v2.1.0 (commit/83a87a41e2, go1.26.4, linux/amd64)
- Generalized abuse-protection feature enabled: true
- Registration protection wired during measurement: false

## Existing registration contract

- endpoint: `POST /api/v1/auth/register`
- successful registration: `201`
- validation failure: `400`
- already-registered email: `409`
- dataset: disposable synthetic `example.invalid` identities
- password: generated at runtime and excluded from evidence

## Measurements

| Phase | Target reg/s | Seconds | Requests | 201 Created | p50 ms | p95 ms | p99 ms | Achieved | Unexpected | Dropped | Health failures | Post-phase app CPU | Saturated |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| warmup | 1 | 10 | 11 | 11 | 255.808 | 398.719 | 500.811 | 110.000% | 0.000% | 0 | 0.000% | 5% | False |
| baseline | 1 | 30 | 31 | 31 | 243.535 | 262.373 | 264.413 | 103.333% | 0.000% | 0 | 0.000% | 1.67% | False |
| ramp-2 | 2 | 20 | 40 | 40 | 242.560 | 258.359 | 263.746 | 100.000% | 0.000% | 0 | 0.000% | 4.41% | False |
| ramp-4 | 4 | 20 | 81 | 81 | 234.936 | 246.051 | 251.042 | 101.250% | 0.000% | 0 | 0.000% | 1.26% | False |
| ramp-8 | 8 | 20 | 161 | 161 | 235.017 | 264.821 | 273.153 | 100.625% | 0.000% | 0 | 0.000% | 1.05% | False |
| ramp-16 | 16 | 20 | 321 | 321 | 253.061 | 274.137 | 282.008 | 100.313% | 0.000% | 0 | 0.000% | 10.38% | False |

Baseline comparable: **True**.

Baseline p95: **262.373 ms**.
Ramp-16 p95: **274.137 ms**.
Ramp-16 p99: **282.008 ms**.
Observed ramp-16 versus baseline p95 delta:
**4.484%**.

First saturation: **not observed through 16 registrations/s**.

Every measured request completed with the existing `201` successful-registration
contract. Every phase recorded zero unexpected failures, zero health-probe
failures, and zero dropped iterations.

Recovery: **0.023 seconds**,
within the 30-second recovery budget.

## Decision rationale

The experiment exercised the complete successful registration path, including
BCrypt hashing, user persistence, account-action credential preparation, and
verification-mail enqueue work.

The highest tested stage, 16 registrations/second, achieved its target without
saturation, unexpected failures, health failures, or dropped iterations.
Latency remained bounded relative to the frozen saturation threshold.

Therefore the experiment does **not** demonstrate a material
resource-exhaustion risk that would satisfy the prerequisite for `ACTIVATE`.

Decision: **DEFER** registration protection for v0.15.0.

The <=10% protected-versus-unprotected normal-path p95 regression gate is not
evaluated because no registration protection implementation is justified or
introduced after the prerequisite risk test failed. That comparison becomes
mandatory only if future evidence supports an `ACTIVATE` implementation
checkpoint.

## Limitations

- Developer-workstation experiment only; not production capacity certification.
- The tested ceiling is 16 registrations/second; this does not prove unlimited
  capacity or absence of risk above the tested range.
- Post-phase Docker CPU and memory values are bounded snapshots, not sustained
  or peak resource telemetry.
- The database was intentionally retained between phases inside one fresh
  experiment project so every request exercised a unique successful
  registration.
- Results are environment-specific and are not directly comparable across
  materially different CPU, memory, Docker, Java, or k6 environments.
- This decision preserves the current `201`/`400`/`409` public registration
  contract and introduces no production registration limiter.

## Privacy review

This committed evidence contains only aggregate performance values, bounded
environment metadata, hashes of ignored candidate files, the measured Git
commit, and the explicit decision. It contains no reusable email, password,
token, authentication material, raw client address, Redis key, mail credential,
or generated account identifier.
