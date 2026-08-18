# Abuse-Protection Operations Runbook

## Scope

This runbook covers the generalized abuse-protection controls introduced in
v0.15.0 for:

- email-verification requests
- password-recovery requests
- MFA login-challenge confirmation
- step-up grant issuance

Registration remains configured but is not wired under the reviewed Increment 6
evidence-backed `DEFER` decision. The bounded developer-workstation experiment
did not demonstrate material resource exhaustion through 16 registrations/
second, so v0.15.0 adds no generalized registration limiter and preserves the
existing `201` / `400` / `409` contract. This does not prove absence of risk
above the tested ceiling.

The existing login limiter is a separate control and is not changed by this
runbook.

## Operational invariants

Operators must preserve these security boundaries during investigation and
mitigation:

- `ABUSE_PROTECTION_ENABLED` is an explicit deployment gate; it is not an
  incident-response toggle
- current workflow defaults are `FAIL_CLOSED`
- changing a workflow to `FAIL_OPEN` requires prior security review, ADR and
  threat-model evidence, public-contract tests, and explicit operational
  approval
- Redis quota algorithms, expiration, limits, and key privacy are application
  contracts and must not be changed during incident triage
- the existing login limiter must remain independent and unchanged
- every investigation must use bounded aggregate telemetry rather than raw
  identity or credential material

## Signals

Micrometer emits:

| Metric | Bounded dimensions |
|---|---|
| `payflow.security.abuse_protection.decisions` | `workflow`, `outcome`, `reason` |
| `payflow.security.abuse_protection.redis.failures` | `workflow`, `failure_mode` |

Prometheus exposes these counters with its standard normalized names:

- `payflow_security_abuse_protection_decisions_total`
- `payflow_security_abuse_protection_redis_failures_total`

The dedicated Grafana dashboard is:

    observability/grafana/dashboards/abuse-protection.json

The alert rules are:

    observability/prometheus/rules/abuse-protection-alerts.yml

## Alert response

### PayFlowAbuseProtectionRedisFailures

Severity: `critical`.

The alert fires when at least one Redis dependency failure is observed in a
five-minute window and the condition remains active for one minute.

Investigate in this order:

1. confirm the PayFlow Prometheus target is healthy
2. inspect Redis container/service health and connectivity
3. inspect the dashboard Redis-failure panel by bounded `workflow` and
   `failure_mode`
4. confirm the deployed abuse-protection configuration matches the reviewed
   configuration
5. correlate the start time with deployment, Redis, networking, or host changes

For fail-closed workflows, coarse dependency-unavailable behavior is expected
while Redis cannot make a safe decision. Restore Redis availability or roll back
the faulty infrastructure/application/configuration change.

Do not switch the affected workflow to `FAIL_OPEN` as an incident workaround.

### PayFlowAbuseProtectionBlockingPressure

Severity: `warning`.

The alert fires when a bounded workflow records at least 25 blocked decisions
during ten minutes and the condition remains active for five minutes.

Blocking pressure can represent expected hostile traffic, a client integration
problem, a trusted-client configuration mistake, or a policy false positive.

Investigate using:

1. decision rate by bounded `workflow` and `outcome`
2. blocked decisions by bounded `workflow` and `reason`
3. recent deployment and configuration changes
4. trusted-proxy/client-context configuration and service health
5. known traffic or test activity during the alert window

Do not search by email address, account identifier, JWT subject, or raw client
address to explain the alert.

A quota change is not an emergency mitigation. Proposed limit/window changes
must follow normal review and should be supported by the reviewed Increment 6
performance/false-positive evidence. Developer-workstation results are not
production capacity certification and must not be used to justify silent
threshold retuning.

### PayFlowAbuseProtectionDependencyBypass

Severity: `critical`.

The current default configuration is fail closed. A dependency-bypass alert
therefore indicates that a workflow has been explicitly configured to
`FAIL_OPEN` and Redis enforcement failed during request processing.

Treat this as a security-significant configuration and dependency incident:

1. confirm the exact reviewed deployment configuration
2. identify why the workflow is operating in `FAIL_OPEN`
3. restore Redis availability
4. roll back an unapproved or incorrect configuration deployment
5. verify bypass decisions stop after recovery

Do not silently disable generalized protection and do not change additional
workflows to `FAIL_OPEN`.

## Disabled enforcement

`outcome="disabled"` is emitted when generalized enforcement is bypassed because
the validated policy is disabled before Redis execution.

`ABUSE_PROTECTION_ENABLED=false` is the repository default. In environments
where generalized enforcement is expected to be active, sustained disabled
decisions indicate a deployment/configuration mismatch.

Check deployment configuration and roll back or correct the configuration
through the normal reviewed deployment path. Do not treat disabling protection
as a safe mitigation for Redis or traffic incidents.

## False-positive handling

A suspected false positive must be evaluated from bounded aggregate evidence:

- affected workflow
- bounded rejection reason (`identity`, `client`, or `both`)
- rate and duration of blocking pressure
- deployment/configuration changes
- trusted-client configuration
- approved load/performance evidence

Do not clear individual Redis quota keys as routine mitigation. Targeted key
deletion weakens active enforcement and requires sensitive request targeting.

Document any proposed policy change through the normal issue/PR process with
tests and explicit rationale.

## Safe rollback

Rollback is appropriate when a recent application or configuration deployment
introduced incorrect observability or policy wiring.

A safe rollback:

1. restores the last reviewed application/configuration version
2. preserves fail-closed dependency behavior
3. preserves Redis key privacy and expiration semantics
4. does not reset individual client or identity counters
5. confirms Prometheus target health and alert recovery after deployment
6. records only bounded incident evidence

If the issue is Redis availability, restore the dependency rather than weakening
the enforcement contract.

## Privacy and incident notes

Never copy the following into dashboards, alert labels/annotations, tickets,
chat messages, or incident notes:

- email addresses
- user UUIDs or authenticated JWT subjects
- MFA challenge tokens or digests
- TOTP values
- recovery codes
- step-up grants
- raw client addresses
- Redis keys, counters, or TTL values
- request URIs
- raw exception classes

Safe incident evidence includes alert name, bounded workflow, outcome, reason,
failure mode, timestamps, service health, deployment identifier, and aggregate
counts/rates.

## Validation

Validate Compose and Prometheus provisioning before approving observability
changes:

```powershell
docker compose `
    --profile monitoring `
    config `
    --quiet

docker compose `
    --profile monitoring `
    run `
    --rm `
    --no-deps `
    --entrypoint /bin/promtool `
    prometheus `
    check config `
    /etc/prometheus/prometheus.yml

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

Then run the focused observability contracts and the complete Maven
verification suite before merge.

Reviewed performance evidence lives under `docs/performance/evidence/`.
Registration decision evidence is
`docs/performance/evidence/2026-08-17-registration-defer-f94ffa8.md`.
Never promote ignored raw performance results directly into incident evidence.
