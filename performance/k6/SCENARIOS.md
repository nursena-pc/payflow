# Protected-workflow load scenarios

Checkpoint 2 adds executable k6 definitions for three representative workflows
already covered by generalized abuse protection. These definitions are scenario
infrastructure, not accepted performance evidence. Accepted runs must still
satisfy `docs/performance/abuse-protection-performance-contract.md`.

## Profiles

Every protected-workflow scenario supports two bounded profiles through
`K6_PROFILE`:

- `smoke`: one VU, one iteration, and a 30-second maximum duration
- `steady`: `constant-arrival-rate` with the frozen workstation latency budgets

The steady profile defaults to 10 iterations/second for 120 seconds. Override
only through `K6_RATE`, `K6_DURATION`, `K6_PRE_ALLOCATED_VUS`, and `K6_MAX_VUS`
when the evidence record explicitly captures those values.

## Account-action request

`account-action-request` calls
`POST /api/v1/auth/email-verification/requests` with synthetic identities under
the reserved `example.invalid` domain. It expects the existing coarse `202`
contract and never stores generated identities as evidence dimensions.

Example smoke command:

```powershell
$env:K6_PROFILE = 'smoke'
.\performance\k6\run.ps1 -Scenario account-action-request
```

## MFA challenge confirmation

`mfa-challenge-confirm` calls
`POST /api/v1/auth/mfa/challenges/confirm`. Each successful iteration consumes a
runtime fixture containing one opaque challenge token and one valid MFA proof.
The scenario never commits or prints those values.

## Step-up grant issuance

`step-up-grant` calls `POST /api/v1/users/me/step-up/grants`. Each iteration uses
one runtime fixture containing an access token, stable purpose, and valid MFA
proof. The Authorization credential is never used as a metric tag or committed
result field.

## Runtime credential fixture contract

The checked-in `performance/k6/fixtures/credential-pool.example.json` contains
only unusable `replace-runtime-*` placeholders. Credential-backed scenarios fail
before traffic starts when those placeholders are still present or when the pool
is smaller than the selected workload requires.

Create the real fixture only under the ignored results tree, for example:

```text
performance/results/runtime/credential-pool.json
```

Then point the container at the mounted runtime file:

```powershell
$env:K6_FIXTURE_FILE = '/results/runtime/credential-pool.json'
```

The runtime fixture may contain disposable challenge tokens, MFA proofs, and
access tokens needed by the selected run. It must never be staged, committed,
copied into sanitized evidence, or included in issue/PR comments.

Checkpoint 2 does not define how these one-time credentials are generated in
bulk. Dataset generation and quota-pressure evidence remain separate reviewed
checkpoints so setup cost is not silently mixed into measured request latency.

## Checkpoint 3 — runtime fixtures and quota-pressure correctness

Checkpoint 3 adds a bounded runtime fixture generator for credential-backed
smoke validation. It creates disposable users only through the public PayFlow
HTTP contracts, consumes the email-verification credential already issued by
registration through the isolated Mailpit HTTP view, enables MFA, and writes
the resulting one-time challenge, recovery-code, and access-token material only
to the ignored `performance/results/runtime/credential-pool.json` path. The
generator does not request a second email-verification credential: doing so
would supersede the registration credential and race asynchronous mail
delivery. The generator defaults to one fixture per credential-backed scenario
and is capped at four so fixture setup cannot silently become a load test or
exhaust the independent login limiter.

The setup flow uses PayFlow's RFC 6238-compatible six-digit HMAC-SHA1 TOTP
contract with a 30-second step. No external TOTP service or reusable credential
is required, and sensitive values are never printed.

Example against an already healthy isolated performance stack:

```powershell
.\performance\k6\setup\generate-credential-pool.ps1 `
    -Count 1 `
    -BaseUrl http://localhost:18080 `
    -MailpitUrl http://localhost:18025

$env:K6_FIXTURE_FILE = '/results/runtime/credential-pool.json'
.\performance\k6\run.ps1 -Scenario mfa-challenge-confirm
.\performance\k6\run.ps1 -Scenario step-up-grant

# Delete the sensitive runtime fixture as soon as the smoke run is complete.
Remove-Item .\performance\results\runtime\credential-pool.json -Force
```

The generator removes any stale file at the selected runtime output path before
creating a new pool. If setup fails, do not reuse a file from an earlier run.
The isolated database/Redis volumes should also be discarded after validation.

`account-action-quota-pressure` is a separate correctness scenario: forty
concurrent one-shot requests use distinct synthetic identities from one load
client. With the reviewed email-verification client limit of twenty, every HTTP
response must retain the coarse `202` contract while the bounded Micrometer
counter delta must be exactly twenty `allowed/none` and twenty
`blocked/client`, with zero identity/both blocking and zero dependency bypass.

Run quota pressure only on a fresh isolated Compose project and Redis volume so
prior quota state cannot change the configured boundary. The validator reads
only `/actuator/prometheus` and reports aggregate bounded counts:

```powershell
.\performance\k6\validate-quota-pressure.ps1 `
    -ProjectName payflow-performance-quota `
    -AppPort 18081
```

This checkpoint establishes reproducible fixture setup and concurrency/quota
correctness. Its smoke and quota-pressure runs are not accepted steady-state,
saturation, overload, or registration performance evidence; those measurements
remain later Increment 6 checkpoints.


## Checkpoint 4A — accepted-evidence collection shape

`account-action-evidence` is the measurement-only form of the representative
email-verification request workflow. It uses a namespaced arrival-rate contract
(`PAYFLOW_K6_EVIDENCE_*`) so k6's reserved execution environment cannot override
script-defined scenarios. The measured request path exports only aggregate
custom metrics for request count, request duration, and unexpected failures.
A second one-request/second scenario probes `/api/v1/system/health` throughout
each phase so health loss is observable without mixing health latency into the
protected-workflow latency trend.

`record-protected-evidence.ps1` orchestrates the frozen phases and calls
`run.ps1 -Scenario account-action-evidence` with a bounded summary-export path
under `/results/`. Raw summaries are ignored. The candidate evidence contains
only environment metadata and aggregate measurements; synthetic emails are
request bodies only and are never metric tags or evidence dimensions.

The recorder evaluates steady-state acceptance from the frozen p95/p99,
unexpected-failure, dropped-iteration, and achieved-rate budgets. Saturation is
the first 10/20/40/80 stage where p95 exceeds 1500 ms, unexpected failures reach
1%, dropped iterations are non-zero, or the concurrent health probe observes a
failure. Overload is then measured for 60 seconds and recovery is bounded to 30
seconds, exactly as required by the pre-existing performance contract.
