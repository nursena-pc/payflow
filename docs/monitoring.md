# Local Monitoring

PayFlow includes a local observability stack for monitoring the transactional outbox publisher.

The stack consists of:

- Spring Boot Actuator and Micrometer
- Prometheus
- Grafana
- Prometheus alerting rules

## Architecture

    PayFlow
      |
      | /actuator/prometheus
      v
    Prometheus
      |
      +-- Metrics and PromQL queries
      +-- Transactional outbox alert rules
      |
      v
    Grafana
      |
      +-- PayFlow Transactional Outbox dashboard

## Prerequisites

The local monitoring stack requires:

- Docker
- Docker Compose
- A local `.env` file containing Grafana administrator credentials

Create the local environment file from the committed example:

~~~powershell
Copy-Item .env.example .env
~~~

Set local Grafana credentials in `.env`:

~~~dotenv
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=replace-with-a-secure-local-password
~~~

The real `.env` file must never be committed.

## Start the stack

Start PayFlow, Prometheus, and Grafana:

~~~powershell
docker compose `
    --profile app `
    --profile monitoring `
    up `
    -d `
    --build
~~~

Inspect the running containers:

~~~powershell
docker compose `
    --profile app `
    --profile monitoring `
    ps
~~~

## Local endpoints

| Component | Address |
|---|---|
| PayFlow API | http://localhost:8080 |
| Actuator health | http://localhost:8080/actuator/health |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Prometheus targets | http://localhost:9090/targets |
| Prometheus alerts | http://localhost:9090/alerts |
| Grafana | http://localhost:3000 |
| Outbox dashboard | http://localhost:3000/d/payflow-outbox-overview/payflow-transactional-outbox |

Grafana credentials are read from the local `.env` file.

## Grafana dashboard

Grafana automatically provisions the `PayFlow Transactional Outbox` dashboard from:

    observability/grafana/dashboards/outbox-overview.json

Dashboard provisioning is configured in:

    observability/grafana/provisioning/dashboards/dashboards.yml

The dashboard includes:

- Active outbox backlog
- Age of the oldest active event
- Terminal publishing outcomes
- Polling success ratio
- Event outcome throughput
- Average polling duration
- Backlog history
- Oldest-event age history

The provisioned dashboard is read-only. Repository files remain the source of truth.

## Prometheus alert rules

Transactional outbox rules are stored in:

    observability/prometheus/rules/outbox-alerts.yml

| Alert | Severity | Condition |
|---|---|---|
| `PayFlowMetricsTargetDown` | Critical | PayFlow cannot be scraped for one minute |
| `PayFlowOutboxPollingFailures` | Warning | A polling cycle fails within five minutes |
| `PayFlowOutboxBacklogHigh` | Warning | Backlog remains at or above 100 for five minutes |
| `PayFlowOutboxOldestEventStale` | Critical | Oldest active event remains at least 120 seconds old |
| `PayFlowOutboxTerminalFailures` | Critical | A failed or unresolved outcome occurs within ten minutes |

Prometheus evaluates these rules locally.

Alertmanager is not currently part of the PayFlow stack. Alerts are visible in Prometheus, but they are not delivered to external notification channels.

## Configuration validation

Validate the resolved Compose configuration:

~~~powershell
docker compose `
    --profile app `
    --profile monitoring `
    config `
    --quiet
~~~

Validate the Prometheus configuration and referenced rules:

~~~powershell
docker compose `
    --profile monitoring `
    run `
    --rm `
    --no-deps `
    --entrypoint /bin/promtool `
    prometheus `
    check config `
    /etc/prometheus/prometheus.yml
~~~

Validate only the transactional outbox rules:

~~~powershell
docker compose `
    --profile monitoring `
    run `
    --rm `
    --no-deps `
    --entrypoint /bin/promtool `
    prometheus `
    check rules `
    /etc/prometheus/rules/outbox-alerts.yml
~~~

## Security considerations

Only these Actuator endpoints are publicly permitted:

    /actuator/health
    /actuator/health/**
    /actuator/prometheus

Other Actuator endpoints remain protected.

The public Prometheus endpoint is intended for local Compose networking. Production deployments should restrict access through private networking, network policies, or authenticated infrastructure.

Grafana anonymous access and public user registration are disabled.

## Stop the stack

Stop the containers while preserving named volumes:

~~~powershell
docker compose `
    --profile app `
    --profile monitoring `
    down
~~~

Do not add `--volumes` unless the local PostgreSQL, Redis, Prometheus, and Grafana data should also be deleted.

## Alert notifications

Alert routing, inhibition, Mailpit delivery, and the notification smoke test
are documented in [Local Alert Notifications](alerting.md).
