# Account-Action Mail Delivery Operations Guide

## Purpose

PayFlow persists email-verification and password-recovery mail in a dedicated
protected outbox and delivers it asynchronously over SMTP. The application
transaction commits the user, credential, and protected mail record together;
SMTP availability does not control registration or password-recovery state.

## Required production configuration

The `production` profile requires a configured mail content-protection key.
Generate exactly 32 random bytes and expose the Base64 value through the secret
manager used by the deployment platform.

```powershell
$key = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($key)
[Convert]::ToBase64String($key)
```

Required or commonly configured environment variables:

```text
MAIL_CONTENT_PROTECTION_MODE=configured
MAIL_CONTENT_ENCRYPTION_KEY=<Base64 encoded 32-byte key>
MAIL_HOST=<SMTP host>
MAIL_PORT=<SMTP port>
MAIL_USERNAME=<optional username>
MAIL_PASSWORD=<optional password>
MAIL_SMTP_AUTH=true|false
MAIL_SMTP_STARTTLS_ENABLED=true|false
MAIL_FROM_ADDRESS=no-reply@example.com
MAIL_FROM_NAME=PayFlow
MAIL_OUTBOX_POLLING_ENABLED=true
MAIL_OUTBOX_WORKER_ID=<stable instance identifier>
```

Never place the encryption key, SMTP password, mail body, recipient, link,
credential, or credential digest in source control, command history, logs,
metrics, alerts, or support tickets.

## Local Mailpit workflow

The Compose `app` profile starts Mailpit and configures the application to use
its SMTP endpoint.

```powershell
docker compose --profile app up -d --build
```

Mailpit UI is available on local port `8025`; SMTP is exposed on `1025`. Local
Compose still requires `MAIL_CONTENT_ENCRYPTION_KEY` because the application
runs with the production profile during protected smoke verification.

## Lifecycle

`PENDING` rows contain only AES-GCM-protected content authenticated against
message, user, purpose, recipient, and subject metadata. A worker claims them as
`PROCESSING` with `locked_by`, `locked_at`, and `locked_until`. Success records
`SENT`, `sent_at`, and erases `protected_body`. A retry returns the row to
`PENDING` with a bounded sanitized failure type. Terminal failure records
`FAILED` and erases `protected_body`.

A worker may reclaim a `PROCESSING` row after its lease expires. Attempts never
continue beyond the account-action credential expiry.

## Safe operational queries

The following query exposes bounded lifecycle metadata only:

```sql
SELECT
    purpose,
    status,
    COUNT(*) AS message_count,
    MAX(attempt_count) AS maximum_attempt_count,
    MIN(created_at) AS oldest_created_at
FROM mail_outbox_messages
GROUP BY purpose, status
ORDER BY purpose, status;
```

Do not select `recipient`, `protected_body`, or join to credential material for
routine operations.

## Failure handling

1. Confirm SMTP reachability and credentials without printing secrets.
2. Check counts by `purpose` and `status` using the safe query above.
3. Confirm worker identifiers are unique per running instance.
4. Confirm lease duration exceeds the normal SMTP timeout envelope.
5. Confirm the content-protection key is the same key used when pending rows
   were created.
6. Restore provider service and allow the bounded retry policy to continue.

Do not manually set a failed row back to pending after its credential expiry.
Do not decrypt or copy protected bodies into diagnostics.

## Key rotation

The v0.13.0 adapter accepts one active key. Before replacing it:

1. Disable new account-action requests or keep the maintenance window short.
2. Drain all `PENDING` and `PROCESSING` mail rows.
3. Verify no unresolved protected rows remain.
4. Deploy the new key.
5. Re-enable request traffic.

Automatic active/previous mail keys and in-place re-encryption are later
hardening work.

## Duplicate-delivery boundary

SMTP and PostgreSQL do not share a transaction. A process failure after SMTP
acceptance but before `SENT` persistence can cause a retry. Every row has a
stable `Message-ID`, leases prevent concurrent normal delivery, and attempts
are bounded. Operators must not describe this as exactly-once delivery.
