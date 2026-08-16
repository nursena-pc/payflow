# Local Alert Notifications

PayFlow uses Prometheus Alertmanager to route repository-provisioned alerts and
Mailpit to capture email notifications in the local development environment.

The local notification flow is:

```text
PayFlow /actuator/prometheus
    |
    v
Prometheus alert rules
    |
    v
Alertmanager
    |
    v
Mailpit SMTP
    |
    v
Mailpit web interface
```

## Notification flow

Prometheus loads every `*.yml` file under
`observability/prometheus/rules/` and sends firing alerts to
`alertmanager:9093`. Alertmanager uses
`observability/alertmanager/alertmanager.yml` as the repository source of truth.

The Compose `monitoring` profile provisions Prometheus, Alertmanager, Grafana,
and Mailpit. The `app` profile also starts Mailpit because PayFlow account-action
mail delivery uses the same local SMTP capture service.

## Local endpoints

| Component | Address |
|---|---|
| Prometheus alerts | http://localhost:9090/alerts |
| Alertmanager | http://localhost:9093 |
| Grafana | http://localhost:3000 |
| Mailpit web interface | http://localhost:8025 |
| Mailpit SMTP | localhost:1025 |

These ports are intended for local development only.

## Alert routing

Alertmanager groups on `alertname`, `service`, `component`, and `severity`.

- `severity="critical"` routes to the `critical-email` receiver and local
  address `oncall@payflow.local`
- `severity="warning"` routes to the `warning-email` receiver and local
  address `engineering@payflow.local`
- unmatched alerts use the `null` receiver

Both email receivers use Mailpit and send resolved notifications. The addresses
are development-only routing targets; no external mail delivery is implied.

## Generalized abuse-protection alerts

Increment 5 provisions these bounded alerts:

| Alert | Severity | Trigger |
|---|---|---|
| `PayFlowAbuseProtectionRedisFailures` | Critical | Redis dependency failure count is greater than zero over five minutes and remains active for one minute |
| `PayFlowAbuseProtectionBlockingPressure` | Warning | A workflow records at least 25 blocked decisions over ten minutes and remains above the threshold for five minutes |
| `PayFlowAbuseProtectionDependencyBypass` | Critical | A fail-open dependency bypass occurs over five minutes and remains active for one minute |

The alert rules aggregate only bounded workflow and failure classifications.
Alert annotations must not contain identities, credentials, raw client
addresses, Redis keys, request URIs, or raw exception classes.

## Grouping and inhibition

Alertmanager waits 10 seconds before sending a new group, uses a 30-second group
interval, and repeats unchanged notifications every four hours.

When `PayFlowMetricsTargetDown` is firing at critical severity, warning alerts
with the same `service` and `component` are inhibited. This prevents secondary
warning noise when the metrics target itself is unavailable.

## Configuration validation

Validate the resolved Compose model:

```powershell
docker compose `
    --profile monitoring `
    config `
    --quiet
```

Validate Prometheus and every referenced rule file:

```powershell
docker compose `
    --profile monitoring `
    run `
    --rm `
    --no-deps `
    --entrypoint /bin/promtool `
    prometheus `
    check config `
    /etc/prometheus/prometheus.yml
```

Validate the generalized abuse-protection rule file directly:

```powershell
docker compose `
    --profile monitoring `
    run `
    --rm `
    --no-deps `
    --entrypoint /bin/promtool `
    prometheus `
    check rules `
    /etc/prometheus/rules/abuse-protection-alerts.yml
```

## Notification smoke test

Start the monitoring profile and submit a synthetic warning alert directly to
the local Alertmanager API. Use only bounded synthetic labels; do not insert real
user, credential, client, or Redis data.

```powershell
docker compose `
    --profile monitoring `
    up `
    -d `
    prometheus alertmanager mailpit

$Now = [DateTimeOffset]::UtcNow

$Body = @(
    @{
        labels = @{
            alertname = 'PayFlowLocalNotificationSmoke'
            severity = 'warning'
            service = 'payflow'
            component = 'abuse-protection'
        }
        annotations = @{
            summary = 'Local notification smoke test'
            description = 'Synthetic bounded local alert'
        }
        startsAt = $Now.ToString('o')
        endsAt = $Now.AddMinutes(2).ToString('o')
    }
) | ConvertTo-Json -Depth 5

Invoke-RestMethod `
    -Method Post `
    -Uri 'http://localhost:9093/api/v2/alerts' `
    -ContentType 'application/json' `
    -Body $Body
```

Open Mailpit at `http://localhost:8025` and verify that the warning notification
arrives for `engineering@payflow.local`. The synthetic alert expires after two
minutes.

## Health checks

Use the local readiness endpoints when diagnosing notification delivery:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:9090/-/ready
Invoke-WebRequest -UseBasicParsing http://localhost:9093/-/ready
Invoke-WebRequest -UseBasicParsing http://localhost:8025
```

Also inspect:

```powershell
docker compose `
    --profile monitoring `
    ps
```

## Troubleshooting

If Prometheus shows a firing alert but Mailpit remains empty:

1. confirm Alertmanager is reachable from Prometheus at `alertmanager:9093`
2. inspect Alertmanager status and the active route
3. confirm the alert has `severity="warning"` or `severity="critical"`
4. confirm Mailpit is running and SMTP port `1025` is available inside Compose
5. inspect Alertmanager container logs without copying sensitive request data
   into tickets or incident notes

For generalized abuse-protection alerts, follow the dedicated
[operations runbook](operations/abuse-protection-observability.md).

## Local development scope

Mailpit is a local capture service, not a production notification provider.
Production routing, retention, access control, and receiver ownership are
deployment concerns and must be configured outside this repository's local
Compose defaults.
