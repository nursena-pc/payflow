# Abuse-Protection Performance Contract

## Scope

This document freezes the measurement contract for PayFlow v0.15.0 Increment 6
before any run is accepted as performance evidence. It covers the generalized
abuse-protection workflows delivered through Increment 5 and the separate
registration experiment required by issue #164.

The numbers below are developer-workstation acceptance budgets for reproducible
comparison. They are not production SLOs, capacity guarantees, or regulatory
claims. A budget may be changed only in a reviewed commit made before the run
that is proposed as accepted evidence.

## Tooling contract

The load generator is the official Grafana k6 container pinned to
`grafana/k6:2.1.0`. Load execution stays outside Maven's `test` and `verify`
lifecycles. The repository-level Maven suite validates only harness structure,
privacy boundaries, and documentation contracts.

The Compose overlay is `performance/k6/compose.yml`. It joins the ordinary
PayFlow Compose project so the load generator addresses the application as
`http://app:8080` without depending on host-only networking behavior.

Generated summaries and raw load output belong under `performance/results/`.
That directory is ignored by Git. Only deliberately reviewed, sanitized evidence
may later be committed under `docs/performance/evidence/`.

## Measurement phases

Every accepted protected-workflow run uses these phases unless a scenario
explicitly documents a stricter contract:

1. warm-up: 30 seconds at 5 iterations/second
2. steady state: 120 seconds at 10 iterations/second
3. saturation discovery: 60-second stages at 10, 20, 40, and 80
   iterations/second
4. overload observation: 60 seconds at the first saturated rate plus 50%, or at
   120 iterations/second when saturation was not observed earlier
5. recovery: stop generated load and require the system health endpoint to
   recover within 30 seconds

Arrival-rate scenarios must use k6 arrival-rate executors rather than the legacy
RPS limiter so iteration starts are controlled independently from response
latency.

## Frozen acceptance budgets

### Steady-state protected workflow

For a representative already-protected workflow:

- `p(95)` request duration must be at most 750 ms
- `p(99)` request duration must be at most 1500 ms
- unexpected HTTP/transport failure rate must be below 0.5%
- `dropped_iterations` must be zero
- the achieved iteration rate must reach at least 95% of the configured target
- expected policy-limited outcomes are not counted as unexpected failures

The status/body contract of the workflow determines whether a policy-limited
request is externally distinguishable. In particular, an anti-enumerating
account-action request may remain a successful coarse HTTP response while the
bounded abuse-protection metric records the enforcement outcome.

### Quota-pressure and concurrency safety

Quota-pressure evidence is a correctness gate, not a latency benchmark:

- zero requests may bypass the configured identity/client quota contract
- protected side effects may not exceed the configured quota boundary
- unexpected HTTP/transport failure rate must be zero for a healthy dependency
  stack
- expected blocked/coarse responses must be counted separately from unexpected
  failures
- Redis dependency failures, if deliberately injected in a later checkpoint,
  must retain the configured failure-mode behavior

### Saturation and overload

Saturation is the first stage where any of the following occurs:

- `p(95)` exceeds 1500 ms
- unexpected HTTP/transport failure rate reaches 1%
- k6 records one or more dropped iterations
- the application or a required dependency becomes unhealthy

Saturation discovery is evidence, not an automatic failure of Increment 6. The
accepted record must identify the first saturated stage and the limiting
resource where observable.

During overload, PayFlow must preserve correctness and security contracts even
when latency budgets are exceeded. After generated load stops, `/api/v1/system/health`
must return successfully within 30 seconds. No quota bypass, sensitive-data
exposure, counter corruption, or fail-open change is permitted to improve the
benchmark.

## Registration experiment

Registration remains unprotected by generalized abuse protection at the start
of Increment 6. Its experiment must use disposable generated identities and
must record the current `POST /api/v1/auth/register` behavior before any wiring
change.

The decision is explicitly one of:

- `ACTIVATE`: evidence demonstrates a material resource-exhaustion risk that the
  reviewed abuse-protection wiring mitigates, and the candidate normal-path
  `p(95)` regression at the same low arrival rate is no more than 10%
- `DEFER`: evidence does not justify changing the registration contract in
  v0.15.0, the candidate creates unacceptable behavior/latency, or the result is
  not comparable enough to support activation

An `ACTIVATE` decision requires a separate reviewed implementation checkpoint,
focused HTTP/concurrency/side-effect tests, and a rerun of the relevant evidence.
Performance work must not silently change the existing `201`, `400`, or `409`
registration contract.

## Evidence record

Every accepted run must record at minimum:

- exact Git commit SHA
- host operating system and Docker versions
- CPU and memory available to Docker where measurable
- Java runtime and pinned k6 version
- Compose files, profiles, and application configuration used
- scenario name and request mix
- generated dataset/setup method
- warm-up and measurement duration
- arrival-rate or VU model and concurrency limits
- p50, p95, and p99 latency where meaningful
- achieved request/iteration throughput
- expected policy-limited outcomes
- unexpected HTTP/transport failures
- dropped iterations
- saturation/overload observation
- recovery observation
- known limitations and non-comparability warnings

## Privacy boundary

Committed harness and evidence must never contain real or reusable:

- email addresses or user UUIDs
- JWT access/refresh credentials or authenticated subjects
- passwords
- MFA challenge tokens or TOTP values
- recovery codes or step-up grants
- raw client addresses
- Redis keys, counters, or TTL values
- mail verification/recovery credentials

Runtime-generated test identities and credentials must be disposable. Raw output
that could contain generated request data remains under the ignored
`performance/results/` directory unless a sanitization/review step explicitly
promotes a bounded aggregate into committed evidence.

## Checkpoint 1 exit gate

Checkpoint 1 is complete when the pinned k6 overlay, local runner, harness smoke
scenario, ignored results directory, and executable repository contracts are
committed and the complete Maven suite plus protected CI/Docker Smoke pass.
No load result produced by this checkpoint is accepted as Increment 6 evidence.
