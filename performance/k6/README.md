# PayFlow k6 Harness

This directory contains the external load/performance harness for v0.15.0
Increment 6. It is intentionally outside the Maven unit-test lifecycle.

## Runtime

The Compose overlay pins the official image to `grafana/k6:2.1.0`. Do not use
`latest` for accepted evidence.

The overlay is combined with the repository root `compose.yml`:

```powershell
$env:MAIL_CONTENT_ENCRYPTION_KEY = '<local test key>'
$env:MFA_SECRET_ENCRYPTION_KEY = '<local test key>'
$env:GRAFANA_ADMIN_PASSWORD = 'payflow-performance-local-only'
$env:PAYFLOW_PERFORMANCE_APP_PORT = '18080'
$env:PAYFLOW_PERFORMANCE_MAILPIT_PORT = '18025'

docker compose `
    -p payflow-performance `
    -f compose.yml `
    -f performance/k6/compose.yml `
    --profile app `
    --profile loadtest `
    up -d --build postgres redis kafka mailpit app

.\performance\k6\run.ps1 `
    -Scenario harness-smoke `
    -ProjectName payflow-performance
```

The root Compose file requires `GRAFANA_ADMIN_PASSWORD` during interpolation
even when the monitoring profile is not started. The manual command above uses
a local-only placeholder for that validation-only requirement.

The performance overlay is isolated from an ordinary developer Compose stack.
It removes host publication for PostgreSQL, Redis, and Kafka, replaces the
application's ordinary `8080:8080` mapping with host port `18080`, and exposes
only Mailpit's local HTTP UI/API on host port `18025` for disposable account
verification during performance fixture setup. Mailpit SMTP remains internal to
the Compose network. k6 still reaches PayFlow at `http://app:8080`. Set
`PAYFLOW_PERFORMANCE_APP_PORT` or `PAYFLOW_PERFORMANCE_MAILPIT_PORT` before
Compose startup if either local-only port must change.

The runner assumes the application stack is already healthy in the same Compose
project selected by `-ProjectName`. It supplies the same local-only placeholder
when the variable is absent, validates the combined Compose model, prints the
pinned k6 version, and then runs only the selected scenario. It does not start,
stop, seed, or clean the application. That separation keeps environment
preparation explicit for evidence runs.

## Checkpoint 1

`harness-smoke` performs one request to `/api/v1/system/health`. It proves only
that the pinned load-generator container can resolve and reach the application
through the Compose network. It is not performance evidence.

Representative protected-workflow, quota-pressure, saturation, overload, and
registration scenarios are added in later Increment 6 checkpoints after their
dataset/setup contracts are reviewed.

## Results

Put raw summaries and temporary outputs under `performance/results/`. The
folder is Git-ignored because raw load output is not automatically safe or
comparable evidence.

Only sanitized aggregate evidence that satisfies
`docs/performance/abuse-protection-performance-contract.md` may later be
promoted into committed documentation.


## Checkpoint 4A — protected-workflow evidence recorder

`record-protected-evidence.ps1` is the reviewed collector for the frozen
protected-workflow measurement contract. It owns only its explicitly named
isolated Compose project, requires a clean Git working tree, records the exact
HEAD, and writes raw k6 summaries plus a sanitized candidate record only under
ignored `performance/results/evidence/`.

The recorder runs the phases frozen before evidence collection: 30 seconds at
5 iterations/second for warm-up, 120 seconds at 10 iterations/second for steady
state, 60-second saturation stages at 10, 20, 40, and 80 iterations/second,
then a 60-second overload observation at the first saturated rate plus 50% or at
120 iterations/second when no earlier stage saturates. A one-per-second health
probe runs concurrently with measured traffic, and recovery must succeed within
30 seconds after generated load stops.

Each independent phase clears only the disposable Redis state inside the named
performance project before measurement. This prevents quota state from an
earlier phase from changing the next phase boundary; it does not modify
production quota values or fail-open behavior. Abuse-protection decisions are
recorded only as bounded aggregate deltas, and any dependency bypass or more
than twenty allowed email-verification decisions in a phase aborts collection.

Checkpoint 4A commits the recorder and executable contracts first. Do not treat
a result as accepted evidence until that exact recorder commit has protected CI
and Docker Smoke green. The resulting candidate Markdown remains ignored until
it is separately reviewed and promoted in a later commit.
