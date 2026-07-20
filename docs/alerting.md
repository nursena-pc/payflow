# Local Alert Notifications

PayFlow uses Prometheus Alertmanager to route transactional outbox alerts and
Mailpit to capture email notifications in the local development environment.

The local notification flow is:

```text
PayFlow metrics
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

## Notification flow
## Local endpoints
## Alert routing
## Grouping and inhibition
## Configuration validation
## Notification smoke test
## Health checks
## Troubleshooting
## Local development scope
