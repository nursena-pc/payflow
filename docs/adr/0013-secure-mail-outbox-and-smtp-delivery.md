# ADR 0013: Secure Mail Outbox and SMTP Delivery

- Status: Accepted
- Date: 2026-08-05
- Decision owners: PayFlow maintainers

## Context

Email verification and password recovery create short-lived account-action
credentials inside database transactions. Sending SMTP in those transactions
would couple registration and recovery correctness to a remote provider. A
provider timeout could roll back valid account state, while a successful SMTP
call followed by a database rollback could deliver a link that the system did
not persist.

The delivery payload contains a provider-ready URL whose query parameter is an
opaque credential. The credential is already stored only as a SHA-256 digest in
`account_action_credentials`; copying the plaintext URL into an ordinary event
outbox, log, metric, trace, or exception would defeat that boundary.

SMTP also has no transactional acknowledgement shared with PostgreSQL. A worker
can receive an SMTP acknowledgement and terminate before recording `SENT`.
Therefore exactly-once external delivery cannot be claimed.

## Decision

PayFlow uses a dedicated `mail_outbox_messages` table and a mail-specific
hexagonal boundary.

1. Credential issuance, configured link construction, content protection, and
   mail-outbox persistence occur in the same PostgreSQL transaction.
2. SMTP is invoked only by an asynchronous dispatcher after that transaction
   commits.
3. The provider-ready body is protected with AES-256-GCM before persistence.
   Each record uses a fresh 96-bit nonce. Authenticated metadata binds the
   ciphertext to its message, user, purpose, recipient, and subject so a
   protected body cannot be transplanted to another outbox row.
4. Production requires a configured 32-byte Base64 key. Local development may
   use a process-local ephemeral key, but pending rows created with that key are
   intentionally not restart-durable.
5. The outbox row identifier equals the account-action credential identifier,
   but no database foreign key couples mail retention to credential cleanup. A
   stable RFC-style `Message-ID` is derived from that identifier without
   containing the credential value.
6. Reissuing a credential marks unresolved mail for the same user and purpose
   as `FAILED` and erases its protected body before the replacement is saved.
7. Workers claim rows with PostgreSQL `FOR UPDATE SKIP LOCKED`, a bounded lease,
   and an attempt counter. Expired leases are recoverable by another worker.
8. Retry uses bounded exponential backoff and never schedules an attempt beyond
   the account-action credential expiry.
9. `SENT` and terminal `FAILED` rows erase protected content. Operational state
   retains only bounded status, timing, attempt, purpose, and sanitized failure
   type data.
10. Logs and metrics never include recipients, subjects, protected bytes,
    plaintext content, links, credentials, or digests.

## Delivery semantics

The dispatcher provides at-least-once delivery with bounded duplicate risk, not
exactly-once SMTP. A crash after the SMTP server accepts a message but before
PostgreSQL records `SENT` can cause one retry. The stable `Message-ID`, leasing,
and bounded attempt policy prevent uncontrolled duplication and allow provider
or downstream deduplication where supported.

A newly issued credential can supersede a message that another worker has
already started sending. In that race, the older link may still reach the
recipient, but credential validation rejects it because the corresponding
credential is superseded. Account-state correctness does not depend on SMTP.

## Consequences

### Positive

- Registration and password-recovery transactions do not depend on SMTP.
- Provider-ready links are not durably stored in plaintext.
- Mail retries and worker concurrency are explicit and testable.
- Terminal rows retain audit-friendly lifecycle metadata without retaining the
  sensitive body.
- SMTP and template choices remain outside user domain code.

### Trade-offs

- Production must manage and back up one symmetric content-protection key.
- Key rotation requires draining or re-encrypting pending rows before retiring
  the old key; automatic multi-key rotation is not part of v0.13.0.
- Exactly-once delivery is impossible without provider cooperation.
- The first implementation sends plain-text transactional mail only.

## Rejected alternatives

### Send SMTP inside registration or recovery transactions

Rejected because the remote provider cannot participate in the PostgreSQL
transaction and would weaken rollback correctness.

### Reuse the Kafka transactional outbox

Rejected because the generic event payload and Kafka observability surfaces are
not an appropriate place for provider-ready account-action URLs.

### Store plaintext templates or links in the mail outbox

Rejected because database access would expose a usable credential even though
the credential table itself stores only a digest.

### Claim exactly-once delivery

Rejected because SMTP acknowledgement and PostgreSQL state cannot be committed
atomically.
