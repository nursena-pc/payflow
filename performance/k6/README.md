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
It removes host publication for PostgreSQL, Redis, Kafka, and Mailpit, and
replaces the application's ordinary `8080:8080` mapping with host port `18080`
by default. k6 still reaches PayFlow over the internal Compose network at
`http://app:8080`. Set `PAYFLOW_PERFORMANCE_APP_PORT` before Compose startup if
a different host-only health-check port is required.

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
