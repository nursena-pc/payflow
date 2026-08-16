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
