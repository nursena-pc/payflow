# PayFlow Postman Collection

The Postman assets provide an executable local workflow for the PayFlow simulated digital-wallet API, manually gated Kafka dead-letter operations, a separate deliberately disruptive login rate-limit verification workflow, and a dedicated manual compatibility-coverage collection for lifecycle-sensitive operations.

## Import

Import the standard workflow assets:

- `PayFlow.postman_collection.json`
- `PayFlow.local.postman_environment.json`

Select the **PayFlow Local** environment.

Import `PayFlow.login-rate-limit.postman_collection.json` separately when
verifying the Redis-backed identity threshold. The dedicated collection has its
own safe collection variables and does not require committed credentials.

Import `PayFlow.mfa.postman_collection.json` separately for the manual MFA
security workflow. It uses the shared local environment but requires
`mfaEmail`, `mfaPassword`, `mfaAccessToken`, and fresh `mfaCode` values to be
supplied locally. Committed MFA credential variables are empty.

Import `PayFlow.api-compatibility.postman_collection.json` separately for
the five source-backed operations that were not represented in the earlier
scenario collections: email-verification confirmation, refresh rotation,
current-session logout, logout-all, and pending MFA-enrollment cancellation.
The collection is intentionally manual. `emailVerificationCredential` and
`refreshToken` are empty secret environment values; `accessToken` and
`mfaAccessToken` must also be supplied or produced locally before the
corresponding request is sent. Across the four executable collections, all
30 canonical `/api/v1` operations are represented without introducing a
Postman-only API operation.
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

The Authentication folder also contains password-recovery request and confirmation examples. The request always expects `202 Accepted`. Before running confirmation, copy the opaque credential received through a trusted delivery or test channel into the secret `passwordRecoveryCredential` environment value. The committed environment intentionally leaves that value empty. `replacementPassword` is a local-only secret variable and should never be exported after use.

Run **Operations** separately. It is not part of the unattended application workflow because it requires both:

- a valid JWT whose claims contain `role=ADMIN`
- an existing Kafka dead-letter record UUID selected deliberately by the operator for record commands
- an existing command-audit UUID selected deliberately by the operator for timeline inspection

The public registration and login workflow does not grant operations authority automatically.

## Generalized abuse-protection workflow notes

v0.15.0 generalized abuse protection covers email-verification requests,
password-recovery requests, MFA login-challenge confirmation, and step-up grant
issuance when the deployment gate is enabled.

The standard collection includes source-user email-verification and
password-recovery request examples. Both account-action request endpoints retain
the same empty `202 Accepted` response for eligible, ineligible, quota-limited,
and fail-closed dependency outcomes; blocked work creates no protected
credential or mail-delivery side effect.

The MFA collection covers challenge confirmation and both purpose-bound step-up
grant requests. Generalized protection runs before challenge, second-factor, or
grant mutation. Policy-limited requests reuse the existing coarse public
failure contracts, and fail-closed Redis dependency failures retain the
`MFA_SECURITY_UNAVAILABLE` contract.

Registration remains outside generalized abuse-protection wiring in v0.15.0
under the reviewed Increment 6 `DEFER` decision. Its existing `201` / `400` /
`409` public contract is unchanged.

`ABUSE_PROTECTION_ENABLED` remains an explicit deployment gate and defaults to
`false`. The existing login limiter is a separate compatibility contract; use
the dedicated login-rate-limit collection for its intentionally disruptive
threshold workflow.

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

The repository contains no real JWTs, password-recovery credentials, privileged operator credentials, personal credentials, or production secrets.

`emailVerificationCredential` and `refreshToken` are committed only as empty secret environment values for the manual compatibility collection. Never export or commit the environment after populating them. `operatorAccessToken`, `deadLetterRecordId`, `auditCommandId`, and `auditOperatorId` are intentionally empty in the committed environment.

Never commit a Postman environment exported after it contains a live user or admin token. Treat an admin JWT as a privileged credential and keep it in a local environment or another trusted secret store.


## MFA security workflow

Run **PayFlow MFA Security Verification** separately from the standard wallet
workflow. Use an active, email-verified account and never export or commit the
environment after supplying credentials.

1. Set `mfaEmail`, `mfaPassword`, and a valid `mfaAccessToken` locally.
2. Run **Enrollment / Get MFA Status** and confirm the state is `DISABLED`.
3. Run **Begin MFA Enrollment** and import the returned provisioning value into
   an authenticator without storing it in collection variables.
4. Set a fresh six-digit `mfaCode`, then run **Confirm MFA Enrollment**.
5. Copy the returned recovery codes directly to an appropriate secure store;
   the collection intentionally does not retain them.
6. Run the login-challenge folder in order, updating `mfaCode` before challenge
   confirmation.
7. Run recovery-code rotation only after setting a fresh proof. Copy the
   replacement codes from the response because the collection clears the
   consumed grant and does not persist plaintext codes.
8. Run the MFA-disable folder only when account MFA removal is intentional.
   Disable is destructive: it removes authenticator and recovery-code state and
   revokes active refresh-token families.

`mfaPassword`, `mfaAccessToken`, `mfaCode`, `mfaChallengeToken`, and
`mfaStepUpGrant` are secret environment variables with empty committed values.
The workflow never creates variables for provisioning secrets or plaintext
recovery-code sets.

## API documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
