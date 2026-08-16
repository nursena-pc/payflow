# Local Monitoring

PayFlow includes a local observability stack for application health, transactional
outbox delivery, Kafka consumer failures, and generalized abuse protection.

The stack consists of:

- Spring Boot Actuator and Micrometer
- Prometheus
- Prometheus alerting rules
- Alertmanager
- Grafana
- Mailpit for local notification capture

## Architecture

    PayFlow
      |
      | /actuator/prometheus
      v
    Prometheus
      |
      +-- Metrics and bounded PromQL queries
      +-- Outbox, Kafka-consumer, and abuse-protection rules
      |
      +------> Alertmanager ------> Mailpit
      |
      v
    Grafana
      |
      +-- Transactional Outbox dashboard
      +-- Kafka Consumer Failures dashboard
      +-- Abuse Protection dashboard

Prometheus loads every committed rule file from
`observability/prometheus/rules/*.yml` and forwards firing alerts to the
repository-provisioned Alertmanager service.

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
| Alertmanager | http://localhost:9093 |
| Grafana | http://localhost:3000 |
| Mailpit | http://localhost:8025 |
| Outbox dashboard | http://localhost:3000/d/payflow-outbox-overview/payflow-transactional-outbox |
| Kafka failures dashboard | http://localhost:3000/d/payflow-kafka-consumer-failures/payflow-kafka-consumer-failures |
| Abuse-protection dashboard | http://localhost:3000/d/payflow-abuse-protection/payflow-abuse-protection |

Grafana credentials are read from the local `.env` file.

## Grafana dashboards

Grafana provisions repository-owned, read-only dashboards from:

    observability/grafana/dashboards/

The current dashboards are:

- `outbox-overview.json` — transactional outbox backlog, age, polling, and outcomes
- `kafka-consumer-failures.json` — bounded consumer failure, retry, and recovery signals
- `abuse-protection.json` — bounded workflow decisions, rejection reasons, disabled
  enforcement, dependency bypass, and Redis dependency failures

Dashboard provisioning is configured in:

    observability/grafana/provisioning/dashboards/dashboards.yml

Repository JSON files remain the source of truth. Dashboard queries must stay
bounded and must not introduce identity, credential, raw client, Redis-key,
request-URI, or raw exception dimensions.

## Prometheus alert rules

Prometheus loads the committed rule directory:

    observability/prometheus/rules/

Current rule files cover transactional outbox, Kafka consumer failures, and
generalized abuse protection. Increment 5 adds:

    observability/prometheus/rules/abuse-protection-alerts.yml

| Alert | Severity | Condition |
|---|---|---|
| `PayFlowAbuseProtectionRedisFailures` | Critical | At least one Redis dependency failure is observed in a five-minute window and remains firing for one minute |
| `PayFlowAbuseProtectionBlockingPressure` | Warning | A bounded workflow records at least 25 blocked decisions in ten minutes and the condition remains active for five minutes |
| `PayFlowAbuseProtectionDependencyBypass` | Critical | At least one fail-open dependency-bypass decision is observed in five minutes and remains firing for one minute |

Alertmanager is part of the monitoring profile. Prometheus forwards firing
alerts to `alertmanager:9093`; Alertmanager routes warning and critical alerts
to Mailpit-backed local email receivers. See [Local Alert
Notifications](alerting.md) and the [Abuse-Protection Operations
Runbook](operations/abuse-protection-observability.md).

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

Validate the generalized abuse-protection rules:

~~~powershell
docker compose `
    --profile monitoring `
    run `
    --rm `
    --no-deps `
    --entrypoint /bin/promtool `
    prometheus `
    check rules `
    /etc/prometheus/rules/abuse-protection-alerts.yml
~~~

The Prometheus configuration loads the complete `*.yml` rule directory, so
`check config` remains the aggregate provisioning gate.

## Security considerations

Only these Actuator endpoints are publicly permitted:

    /actuator/health
    /actuator/health/**
    /actuator/prometheus

Other Actuator endpoints remain protected.

The public Prometheus endpoint is intended for local Compose networking. Production deployments should restrict access through private networking, network policies, or authenticated infrastructure.

Grafana anonymous access and public user registration are disabled.

Alertmanager and Mailpit are local-development services in the Compose
monitoring profile. Production notification routing must use deployment-owned
private networking, authenticated infrastructure, and organization-approved
receivers rather than exposing these local ports publicly.

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
