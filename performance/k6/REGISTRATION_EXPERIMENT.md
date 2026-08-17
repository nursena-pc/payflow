# Registration performance experiment

## Purpose

This experiment supplies the final evidence needed for the v0.15.0 Increment 6
registration `ACTIVATE` or `DEFER` decision. It measures the existing
`POST /api/v1/auth/register` behavior before any generalized abuse-protection
wiring is added.

The experiment is developer-workstation evidence only. It is not a production
SLO or capacity certification.

## Existing contract

Registration keeps the current public contract throughout this experiment:

- `201` for a newly registered user
- `400` for request validation failure
- `409` for an already-registered email address

The registration application path normalizes the email address, checks
uniqueness, hashes the password with BCrypt, persists the user, creates an
email-verification credential, and enqueues verification mail preparation.

## Reproducible method

The official k6 image remains pinned to `grafana/k6:2.1.0`. Load execution stays
outside Maven `test` and `verify`.

A fresh named Compose project is used for each accepted candidate. The default
project name is `payflow-performance-registration`.

The experiment uses one registration request stream plus one independent health
probe per second:

1. warm-up: 10 seconds at 1 registration/second
2. low-rate baseline: 30 seconds at 1 registration/second
3. ramp: 20 seconds each at 2, 4, 8, and 16 registrations/second
4. recovery: application health must succeed within 30 seconds after load stops

Every registration uses a unique disposable `example.invalid` identity. The
password is generated at recorder runtime, is passed only through environment
state, is never printed, and is never committed.

The database is intentionally retained across phases inside the fresh
experiment project so every request exercises the complete successful
registration path rather than a duplicate-email path.

## Measurements

Every phase records:

- target and achieved registration rate
- successful `201` count
- p50, p95, and p99 request latency
- unexpected HTTP/transport failure rate
- dropped iterations
- health-probe failure rate
- configured/observed VU capacity where available
- a bounded post-phase Docker CPU and memory snapshot for the app container

A post-phase Docker resource snapshot is not peak or sustained telemetry. It is
kept only as a bounded workstation resource signal and must be interpreted with
that limitation.

For consistency with the already-frozen performance contract, a phase is marked
saturated when p95 exceeds 1500 ms, unexpected failure rate reaches 1%, dropped
iterations are non-zero, or the health probe fails.

## Decision boundary

The recorder never chooses `ACTIVATE` or `DEFER` and never changes production
wiring.

Review supports `DEFER` when the bounded unprotected experiment does not
demonstrate a material resource-exhaustion risk sufficient to justify changing
registration behavior in v0.15.0.

Review may proceed toward `ACTIVATE` only when the experiment demonstrates a
material resource-exhaustion risk. Activation then requires a
separate reviewed implementation checkpoint, preservation of the `201`/`400`/`409` contract,
focused correctness/concurrency/side-effect tests, and a comparable low-rate
candidate measurement whose p95 regression is no more than 10%.

## Privacy

Committed harness and reviewed evidence must not contain reusable emails,
passwords, tokens, client addresses, Redis keys, raw counters, mail credentials,
or generated authentication material. Raw experiment output remains under the
ignored `performance/results/` tree.
