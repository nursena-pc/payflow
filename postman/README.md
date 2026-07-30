# PayFlow Postman Collection

The Postman assets provide an executable local workflow for the PayFlow simulated digital-wallet API, manually gated Kafka dead-letter operations, and a separate deliberately disruptive login rate-limit verification workflow.

## Import

Import the standard workflow assets:

- `PayFlow.postman_collection.json`
- `PayFlow.local.postman_environment.json`

Select the **PayFlow Local** environment.

Import `PayFlow.login-rate-limit.postman_collection.json` separately when
verifying the Redis-backed identity threshold. The dedicated collection has its
own safe collection variables and does not require committed credentials.

## Prerequisites

```bash
docker compose up -d postgres redis kafka
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
docker compose up -d postgres redis kafka
.\mvnw.cmd spring-boot:run
```

## Recommended run order

Run the standard application workflow folders in this order:

1. System
2. Authentication
3. Users
4. Wallets
5. Transfers
6. Transactions

The registration requests generate unique source and target emails. Login requests save their JWTs. Wallet requests save wallet IDs. `Create Transfer` generates an `Idempotency-Key`.

Run **Operations** separately. It is not part of the unattended application workflow because it requires both:

- a valid JWT whose claims contain `role=ADMIN`
- an existing Kafka dead-letter record UUID selected deliberately by the operator for record commands
- an existing command-audit UUID selected deliberately by the operator for timeline inspection

The public registration and login workflow does not grant operations authority automatically.

## Login rate-limit workflow

Run **PayFlow Login Rate-Limit Verification** separately from the normal
application workflow. It intentionally consumes login attempts.

1. Ensure Redis and the application are running with the default identity limit
   of five attempts.
2. Run the complete **Identity Threshold** folder in order.
3. Attempt 1 generates a unique unregistered email address.
4. Attempts 1 through 5 verify the generic `401 INVALID_CREDENTIALS` contract.
5. Attempt 6 verifies `429 LOGIN_RATE_LIMIT_EXCEEDED` and a positive
   `Retry-After` header.

The workflow uses no real account and clears its generated collection variable
after the blocked attempt. Rerun the folder from attempt 1 rather than sending
individual requests out of order.

To verify fail-closed behavior manually, stop Redis after the application has
started and send a login request. The expected response is
`503 LOGIN_RATE_LIMIT_UNAVAILABLE`. Restore Redis before running the standard
authentication workflow.

See [`../docs/login-rate-limiting.md`](../docs/login-rate-limiting.md) for the
complete security and operations contract.
## Operations workflow

1. Obtain an admin JWT from a trusted local identity or test setup.
2. Store it only in the active Postman environment as `operatorAccessToken`.
3. Run `List Kafka Dead-Letter Records`.
4. Deliberately select a returned record and copy its `id` into `deadLetterRecordId`.
5. Run `Get Kafka Dead-Letter Record`.
6. Run either `Replay Kafka Dead-Letter Record` or `Discard Kafka Dead-Letter Record` after reviewing the record state.
7. Run `List Kafka Dead-Letter Command Audits` after a command attempt.
8. Copy a returned `commandId` into `auditCommandId`.
9. Run `Get Kafka Dead-Letter Command Audit Timeline`.

Replay and discard mutate lifecycle state. Do not run both commands blindly against the same record. A replayable record normally starts in `RECEIVED` or `REPLAY_FAILED`; discard accepts those same eligible states and is idempotent after the record reaches `DISCARDED`.

The dead-letter list endpoint accepts optional `page`, `size`, and `status` parameters. Supported status values are `RECEIVED`, `REPLAYING`, `REPLAYED`, `REPLAY_FAILED`, and `DISCARDED`.

The command-audit list uses `auditPage` and `auditSize`. Disabled filters are available for `auditCommandId`, `auditOperatorId`, `deadLetterRecordId`, command type, stage, and outcome. Enable filters deliberately. Audit entries are ordered by occurrence time descending and audit UUID descending. The timeline request requires a valid `auditCommandId` and returns chronological entries with a `complete` flag.

Operations requests validate that `operatorAccessToken` and, where required, `deadLetterRecordId` or `auditCommandId` are configured before sending the request.

## Replay verification

Run `Create Transfer`, then run `Replay Last Transfer` without rerunning the first request. The replay must return the same transaction ID without changing balances again.

## Security

The repository contains no real JWTs, privileged operator credentials, personal credentials, or production secrets. `operatorAccessToken`, `deadLetterRecordId`, `auditCommandId`, and `auditOperatorId` are intentionally empty in the committed environment.

Never commit a Postman environment exported after it contains a live user or admin token. Treat an admin JWT as a privileged credential and keep it in a local environment or another trusted secret store.

## API documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
