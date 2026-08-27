# v1.0.0 Observability and Performance Release-Budget Contract

Status: Active v1.0.0 release-candidate observability/performance contract

Tracking issue: #197

Baseline: `8a49e8bcd05ba64fd07b87bdeca5dc415e87dda3`

## Diagnostic outcome

The CP4 read-only diagnostic completed from the exact clean baseline without
repository mutation. Structured logging, request/correlation handling,
redaction, bounded metrics, monitoring configuration, alerting contracts, and
the repository-owned k6 harness remained coherent.

No runtime observability defect, metric-contract defect, alert-contract defect,
or performance-tuning release blocker was identified.

The performance Compose contract and k6 executable contract both passed before
measurement. No runtime, public API, database schema/migration, dependency,
workflow, metric, alert, quota, retry, or security behavior was changed.

## Fresh v1 bounded performance evidence

Repository-approved protected-workflow evidence was rerun from exact commit:

`8a49e8bcd05ba64fd07b87bdeca5dc415e87dda3`

Run identifier: `20260827T152405Z`

Promoted evidence:

`docs/performance/evidence/2026-08-27-protected-workflow-8a49e8b.md`

Source candidate integrity:

- Markdown SHA-256: `183cfb6a31f410d9099c95ea3a02d76d44ce672cf0362a04fc04415342eaa994`
- JSON SHA-256: `52c720bf0e458634153fd09f31a0fb8a4bb255106214474cc3871d33584bd351`

The source candidate was independently reviewed before promotion.

## Steady-state release budget

| Gate | Budget | Observed | Verdict |
|---|---:|---:|---|
| p95 request duration | <= 750 ms | 9.666 ms | PASS |
| p99 request duration | <= 1500 ms | 14.344 ms | PASS |
| unexpected failure rate | < 0.5% | 0% | PASS |
| dropped iterations | 0 | 0 | PASS |
| achieved target rate | >= 95% | 100.083% | PASS |
| health-probe failure rate | 0% | 0% | PASS |

Steady-state verdict: **ACCEPTED**.

## Security and recovery boundary

Every measured phase remained within the reviewed twenty-decision client
boundary. Identity-only blocked decisions, combined identity/client blocked
decisions, and dependency-bypass decisions remained zero.

Recovery completed in **0.038 seconds**, inside the frozen 30-second
budget.

First saturation: **not observed through the reviewed saturation stages**.

Saturation and overload results are bounded synthetic developer-workstation
observations only. They are not production capacity measurements.

## v1 scope boundary

CP4 does not add or activate:

- a new observability vendor or telemetry platform;
- distributed tracing or production SLO/SLA commitments;
- metric labels containing identities, raw client addresses, credentials, or
  other unbounded sensitive dimensions;
- arbitrary performance tuning to improve benchmark numbers;
- weakened quota, retry, security, or failure-mode behavior;
- public API or database-schema changes;
- Kubernetes, gateway, CDN, WAF, service-mesh, or deployment redesign;
- production-capacity, regulatory-certification, or real-money claims.

The fresh evidence therefore closes the measured v1 performance-budget review
without runtime expansion.
