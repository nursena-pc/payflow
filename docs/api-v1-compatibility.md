# `/api/v1` Compatibility Baseline

Issue: [#171](https://github.com/nursena-pc/payflow/issues/171)

Baseline commit:
`a9620d360025f101b354d1d2469a98ee2936afc2`

This document freezes the implementation-backed PayFlow `/api/v1` compatibility
surface for the v0.16.0 stabilization line. It is an inventory and review
boundary, not a new feature specification.

PayFlow remains a simulated-money modular monolith. This baseline does not
activate generalized registration abuse protection, change quotas, add
endpoints, redesign DTOs, or introduce `/api/v2`.

## Canonical inventory rule

The canonical compatibility key is **HTTP method + normalized route path**.
Query-string examples do not create additional endpoints.

Source verification establishes:

- **30 canonical HTTP operations**
- **28 unique route paths**
- the difference is expected because `/api/v1/users/me/mfa` has both `GET` and
  `DELETE`, while `/api/v1/users/me/mfa/enrollment` has both `POST` and
  `DELETE`
- all operator audit routes under
  `/api/v1/operations/kafka/dead-letter-command-audits` are part of the
  canonical surface even though their controller class-level mapping is split
  across concatenated Java string literals

The generated OpenAPI path contract enumerates the same **28 unique paths**.
The README contains the same **30 canonical method/path operations** plus two
query-string examples in the Kafka operations section; those examples are not
additional endpoints.

## Public identity and system operations

| Method | Route | Boundary | Request shape | Success | Coarse documented failures | Compatibility-sensitive semantics |
|---|---|---|---|---|---|---|
| `GET` | `/api/v1/system/health` | Public | none | `200` | health content is configuration-dependent | Health output must not expose credentials or privileged operational data. |
| `POST` | `/api/v1/auth/register` | Public | `RegisterUserRequest(email, password)` | `201` | `400`, `409` | Registration keeps its existing `201` / `400` / `409` contract and remains outside generalized registration protection under the evidence-backed `DEFER` decision. |
| `POST` | `/api/v1/auth/login` | Public | `AuthenticateUserRequest(email, password)` | `200`, or `202 MFA_REQUIRED` | `400`, `401`, `403`, `429`, `503` | Password login keeps the separate Redis-backed limiter, positive `Retry-After` on `429`, fail-closed `503`, generic invalid-credential behavior, and MFA challenge transition. |
| `POST` | `/api/v1/auth/mfa/challenges/confirm` | Public | `ConfirmMfaLoginChallengeRequest(challengeToken, code)` | `200` | coarse `401`, fail-closed `503` | The challenge is short-lived and single-use; valid TOTP or one unused recovery code may complete it. Abuse protection runs before protected mutation when enabled. |
| `POST` | `/api/v1/auth/email-verification/requests` | Public | `EmailVerificationRequest(email)` | empty `202` | `400`; eligible/ineligible/quota/fail-closed outcomes intentionally share the accepted response | Account existence and eligibility remain non-enumerable. Blocked work creates no protected credential or mail side effect. |
| `POST` | `/api/v1/auth/email-verification/confirm` | Public | `EmailVerificationConfirmRequest(credential)` | `204` | `400`, `422` | The opaque credential is single-use and ownership is marked exactly once. |
| `POST` | `/api/v1/auth/password-recovery/requests` | Public | `PasswordRecoveryRequest(email)` | empty `202` | `400`; eligible/ineligible/quota/fail-closed outcomes intentionally share the accepted response | Account existence and eligibility remain non-enumerable. Blocked work creates no protected credential or mail side effect. |
| `POST` | `/api/v1/auth/password-recovery/confirm` | Public | `PasswordRecoveryConfirmRequest(credential, newPassword)` | `204` | `400`, `422` | The opaque credential is single-use; password replacement and active-session revocation remain atomic. |
| `POST` | `/api/v1/auth/refresh` | Public | `RotateRefreshCredentialsRequest(refreshToken)` | `200` | `400`, `401` | Refresh credentials remain opaque, digest-only at persistence, atomically rotated, and protected by reuse/family-revocation semantics. |
| `POST` | `/api/v1/auth/logout` | Public | `RevokeCurrentRefreshSessionRequest(refreshToken)` | `204` | `400` | Revocation remains idempotent for the family represented by the submitted refresh credential. |

## Authenticated identity and MFA operations

All operations in this section derive identity from the verified JWT subject.
Client-supplied user identifiers are not accepted as substitutes for the
authenticated subject.

| Method | Route | Boundary | Request shape | Success | Coarse documented failures | Compatibility-sensitive semantics |
|---|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/logout-all` | Bearer JWT | none | `204` | `401`, `500` | Revokes every active refresh session owned by the authenticated subject; response exposes no credential/session inventory. |
| `GET` | `/api/v1/users/me` | Bearer JWT | none | `200` | `401`, `404` | Returns only safe current-user profile fields. |
| `GET` | `/api/v1/users/me/mfa` | Bearer JWT | none | `200` | authentication plus bounded account/state failures | Returns lifecycle metadata without TOTP secret, provisioning URI, recovery-code plaintext, or protected bytes. |
| `POST` | `/api/v1/users/me/mfa/enrollment` | Bearer JWT + current password | `BeginMfaEnrollmentRequest(currentPassword)` | `200` | validation/authentication plus coarse `401`, `403`, `404`, `409`, `503` families | Creates at most one effective pending enrollment and returns provisioning secret material only in the response that created it. |
| `POST` | `/api/v1/users/me/mfa/enrollment/confirm` | Bearer JWT | `ConfirmMfaEnrollmentRequest(code)` | `200` | validation/authentication plus coarse `401`, `403`, `404`, `409`, `503` families | Activates only after valid proof and returns the complete plaintext recovery-code set once. |
| `DELETE` | `/api/v1/users/me/mfa/enrollment` | Bearer JWT | none | `204` | authentication plus coarse account/state failure families | Cancels only pending enrollment and removes its protected pending secret state. |
| `POST` | `/api/v1/users/me/step-up/grants` | Bearer JWT + enabled MFA proof | `IssueStepUpGrantRequest(purpose, code)` | `200` | `400`, `401`, `403`, `404`, `409`, `503` | Grants remain short-lived, opaque, subject-bound, purpose-bound, digest-only at persistence, superseding, and single-use. |
| `POST` | `/api/v1/users/me/mfa/recovery-codes/rotation` | Bearer JWT + `recovery-code-rotation` step-up grant | `RotateMfaRecoveryCodesRequest(stepUpGrant)` | `200` | validation/authentication plus coarse `400`, `401`, `403`, `404`, `409`, `503` families | Consumes the exact step-up purpose, atomically replaces the full digest set, and returns replacement plaintext once. |
| `DELETE` | `/api/v1/users/me/mfa` | Bearer JWT + `mfa-disable` step-up grant | `DisableMfaRequest(stepUpGrant)` | `204` | validation/authentication plus coarse `400`, `401`, `403`, `404`, `409`, `503` families | Consumes the exact step-up purpose, removes authenticator/recovery state, revokes active refresh families, and appends credential-free audit evidence atomically. |

## Wallet, transfer, and transaction operations

These endpoints remain simulated-money only.

| Method | Route | Boundary | Request shape | Success | Coarse documented failures | Compatibility-sensitive semantics |
|---|---|---|---|---|---|---|
| `POST` | `/api/v1/wallets` | Bearer JWT | `OpenWalletRequest(currency)` | `201` | `400`, `401`, `409` | Opens one zero-balance wallet for the authenticated user; one-wallet-per-user enforcement remains authoritative. |
| `GET` | `/api/v1/wallets/me` | Bearer JWT | none | `200` | `401`, `404` | Wallet ownership is derived from the authenticated subject. |
| `POST` | `/api/v1/wallets/me/top-ups` | Bearer JWT | `TopUpWalletRequest(amount)` | `200` | `400`, `401`, `404`, `409`, `422` | Credits only the authenticated user's simulated wallet; currency is derived from the existing wallet. |
| `POST` | `/api/v1/transfers` | Bearer JWT + `Idempotency-Key` | `TransferMoneyRequest(targetWalletId, amount)` | `201`; completed replay returns the existing result | `400`, `401`, `404`, `409`, `422` | Source wallet comes from JWT subject. Completed same-key/same-payload replay does not move money twice; conflicting reuse and unfinished duplicate ownership keep stable `409` families. Debit, credit, transaction, ledger, and outbox persistence remain one PostgreSQL unit of work. |
| `GET` | `/api/v1/transactions/me` | Bearer JWT | query: `page`, `size`, `direction`, `status`, `from`, `to` | `200` | `400`, `401`, `404` | Uses authenticated wallet ownership, deterministic `createdAt DESC, id DESC`, inclusive `from`, exclusive `to`, and excludes transfer idempotency keys from responses. |

## Operator-only Kafka dead-letter operations

`/api/v1/operations/**` requires the application-owned
`PAYFLOW_OPERATIONS` authority, derived from the exact administrative JWT role
contract. A normal authenticated user is forbidden.

| Method | Route | Boundary | Request shape | Success | Coarse documented failures | Compatibility-sensitive semantics |
|---|---|---|---|---|---|---|
| `GET` | `/api/v1/operations/kafka/dead-letters` | operations authority | query: `page`, `size`, optional `status` | `200` | `400`, `401`, `403` | Returns paginated safe metadata only; Kafka payload and record key are excluded. |
| `GET` | `/api/v1/operations/kafka/dead-letters/{recordId}` | operations authority | path UUID `recordId` | `200` | `400`, `401`, `403`, `404` | Authorized details remain bounded to operationally required fields. |
| `POST` | `/api/v1/operations/kafka/dead-letters/{recordId}/replay` | operations authority | path UUID `recordId` | `200` with replayed lifecycle result | `400`, `401`, `403`, `404`, `409`, `500`, `502`, `503` | Replay is lifecycle-controlled, leased, auditable, and fail-closed when resolution/auditing cannot complete safely. |
| `POST` | `/api/v1/operations/kafka/dead-letters/{recordId}/discard` | operations authority | path UUID `recordId` | `204` | `400`, `401`, `403`, `404`, `409`, `500`, `503` | Discard is lifecycle-controlled and idempotent after the record reaches `DISCARDED`. |
| `GET` | `/api/v1/operations/kafka/dead-letter-command-audits` | operations authority | query: `page`, `size`, optional command/operator/record/type/stage/outcome filters | `200` | `400`, `401`, `403` | Returns append-only safe audit metadata ordered deterministically; payloads, keys, JWTs, operator email, exception text, stack traces, and lease owners remain excluded. |
| `GET` | `/api/v1/operations/kafka/dead-letter-command-audits/{commandId}` | operations authority | path UUID `commandId` | `200` | `400`, `401`, `403`, `404` | Returns chronological `ATTEMPTED`/`COMPLETED` evidence; an attempted-only timeline may represent an incomplete command. |

## Security and failure-contract freeze

The following are compatibility-sensitive for v1.0.0 and require an explicit,
reviewed compatibility checkpoint before any breaking change:

- Spring Security remains deny-by-default through `anyRequest().denyAll()`.
- Public identity routes remain explicitly allowlisted; authenticated routes do
  not become public by documentation or convention.
- `/api/v1/operations/**` remains authority-gated.
- Authenticated user/wallet/transaction identity remains derived from the
  verified JWT subject.
- Password login keeps its separate Redis limiter contract, including generic
  invalid credentials, stable `429`, positive `Retry-After`, and fail-closed
  `503`.
- Trusted-client resolution continues to ignore forwarding headers from
  untrusted direct peers and keeps raw client addresses out of logs and metric
  labels.
- Account-action request endpoints preserve empty `202 Accepted`
  anti-enumeration behavior across eligible, ineligible, quota-limited, and
  fail-closed outcomes.
- Generalized abuse protection keeps credential-safe, low-cardinality
  observability and its existing fail-closed boundaries where wired.
- Registration remains the reviewed `DEFER` case and retains its existing
  public `201` / `400` / `409` contract.
- MFA challenge, recovery-code, step-up, account-action, refresh, and other
  opaque credentials remain single-use or rotation-bound according to their
  delivered contracts; plaintext credentials, digests, secrets, and protected
  bytes do not become observable output.
- Transfer idempotency and transactionality remain compatibility-sensitive:
  successful replay cannot create a second financial movement.
- PayFlow continues to make no real-money, regulatory-certification, or
  production-capacity claim.

A later breaking change to these boundaries must not be hidden in refactoring
or documentation cleanup. It requires a dedicated issue, explicit migration or
compatibility analysis, executable verification, and protected review.

## OpenAPI comparison

The current generated OpenAPI contract is source-backed by
`OpenApiJsonContractIntegrationTest` and exposes the same **28 unique
`/api/v1` paths** as this baseline. Two of those paths support two methods,
giving the **30 canonical operations** above.

Current strengths:

- exact route-path presence is executable
- registration, login, account-action, refresh, wallet, transfer, transaction,
  dead-letter, and selected security/status contracts are explicitly tested
- logout-all has a dedicated OpenAPI integration contract
- Bearer JWT configuration is explicit

Recorded OpenAPI follow-up items for the later documentation-drift alignment
increment:

1. `OpenApiConfiguration` still reports `info.version = 0.2.0`; this predates
   the current v0.16.0 development line and must be reviewed rather than
   silently interpreted as the application release version.
2. MFA enrollment/status/cancel/disable/recovery-code controller operations
   rely more heavily on generated/inferred OpenAPI metadata than the explicitly
   annotated identity, wallet, transfer, and operator controllers. Their
   response/security descriptions should be reviewed against the compatibility
   table above.
3. This checkpoint records those documentation gaps but does not add runtime
   behavior or broadly rewrite controller documentation.

## Postman comparison

The standard, MFA, and login-rate-limit collections together currently contain
**25 unique canonical `/api/v1` operations** from this 30-operation baseline.

There are no verified Postman-only API operations once the two concatenated
operator audit routes are resolved correctly from source.

The five implemented operations not currently represented as Postman requests
are:

- `POST /api/v1/auth/email-verification/confirm`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/logout-all`
- `DELETE /api/v1/users/me/mfa/enrollment`

These are recorded as **known executable-workflow coverage gaps**, not missing
API endpoints. The existing collections are scenario-oriented rather than an
exhaustive endpoint manifest. This inventory does not claim an omission is
intentional unless existing workflow documentation supports that conclusion.
Later Postman alignment may add coverage without changing the compatibility
surface.

## Documentation-drift inventory

This checkpoint intentionally does not broadly rewrite architecture
documentation. The following concrete follow-up items are frozen for the later
v0.16 documentation-alignment increment:

1. `docs/architecture.md` still says transactional outbox persistence is
   "planned for a later milestone", while the delivered platform and README
   describe transactional outbox persistence and Kafka publication as
   implemented.
2. The architecture package-convention example still lists `notification` and
   `audit` as top-level capability packages even though the current source tree
   uses delivered packages such as `outbox`, `eventprocessing`,
   `maildelivery`, `abuseprotection`, and `observability`.
3. The architecture document does not yet describe several delivered modules
   and their current boundaries with the same completeness as older wallet,
   transaction, ledger, and client-context sections.
4. `OpenApiConfiguration` still contains the historical `0.2.0` metadata
   version and requires an explicit documentation-version decision.

These are documentation follow-ups. They are not authorization to redesign the
modular monolith, extract microservices, change the database model, or change
the public API.

## Stabilization decision

The implementation-backed `/api/v1` surface is now frozen as the v0.16.0
compatibility baseline. Recovery, migration, dependency-failure, supply-chain,
and clean-environment rehearsals must preserve this surface unless a separately
reviewed compatibility change explicitly supersedes part of it.