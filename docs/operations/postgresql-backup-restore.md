# PostgreSQL Backup and Restore Rehearsal

Historical rehearsal issue: [#173](https://github.com/nursena-pc/payflow/issues/173)
Current v1.0.0 CP5 review: [#199](https://github.com/nursena-pc/payflow/issues/199)

PayFlow uses PostgreSQL as the system of record. This guide originated with the
v0.16.0 backup-and-restore rehearsal and is revalidated for the current v1.0.0
release-candidate line. It proves that a PostgreSQL backup can be restored into
a clean isolated target without changing the source database, Flyway schema,
public `/api/v1` behavior, or security boundaries.

This is a developer/stabilization rehearsal. It is **not** production
disaster-recovery certification.

## Tooling and prerequisites

The committed rehearsal entry point is:

```powershell
.\scripts\operations\postgres-backup-restore-rehearsal.ps1
```

The current project database line is PostgreSQL 17 and `compose.yml` uses
`postgres:17-alpine`.

Before running the rehearsal:

- Docker must be available.
- Java 21 must be available for restored-database application startup.
- the Maven wrapper must be present unless the JAR resolved from the current
  Maven artifact coordinates is intentionally reused with `-SkipPackage`; on
  the release-preparation candidate this is `target/payflow-1.0.0.jar`.
- the Git working tree must be clean and HEAD must be attached to a branch.
- exactly one running Docker container must match the configured Compose project
  and PostgreSQL service labels; defaults are project `payflow`, service
  `postgres`.
- the source database must already be at the latest versioned Flyway migration
  present in the checked-out repository.
- the source PayFlow `app` container must be stopped before the backup window.

The rehearsal deliberately refuses a stale source schema. Migrating a previous
release or older local database to the current schema belongs to the separate
Flyway clean-install/upgrade rehearsal and is not hidden inside backup tooling.

## Safe source selection

The script resolves the source PostgreSQL container from Docker Compose labels
instead of parsing the whole Compose model. This avoids unrelated Compose
environment interpolation from changing source selection.

The script then proves all of the following before creating a backup:

- the selected source container is running PostgreSQL 17 tooling;
- the resolved database is exactly the configured source database;
- Flyway's latest successful version equals the latest checked-in migration;
- the source application container is not running;
- representative delivered persistence tables exist.

The script never restores over the source database and contains no source
database deletion or volume-removal operation.

## Representative persistence boundary

The complete public-table set is compared by table name and row count. The
following delivered state is also required explicitly:

- identity/account state: `users`
- refresh-session state: `refresh_token_families`,
  `refresh_token_records`
- wallet state: `wallets`
- transfer/payment state: `payment_transactions`
- double-entry ledger state: `ledger_entries`
- transactional outbox state: `outbox_events`
- Kafka dead-letter state: `kafka_dead_letter_records`
- operator command-audit state:
  `kafka_dead_letter_command_audits`

Flyway verification compares the successful history-row count, latest version,
and a deterministic digest of non-sensitive Flyway metadata.

No row values are written to committed documentation or sanitized evidence.

## Backup procedure

The script creates a PostgreSQL custom-format backup with PostgreSQL 17 tooling:

```text
pg_dump --format=custom --no-owner --no-privileges
```

The dump is copied only to:

```text
.runtime/postgres-rehearsal/<timestamp>/
```

`.runtime/` is ignored by Git. The dump is removed after a successful or failed
rehearsal unless `-KeepDump` is supplied deliberately.

A SHA-256 digest and byte size are recorded for the local runtime artifact, but
the dump itself is never committed.

The script captures a source fingerprint before and after backup. Any change in
the public table set, row counts, or Flyway metadata aborts the rehearsal
instead of treating a potentially moving backup window as valid evidence.

## Clean isolated restore

The restore target is a new ephemeral PostgreSQL 17 container with:

- a separate container identity;
- a separate database name;
- a random process-local target password;
- a loopback-only dynamically selected host port;
- no persistent target volume;
- automatic container removal after the rehearsal.

The backup is restored with:

```text
pg_restore --exit-on-error --no-owner --no-privileges
```

Partial restore output is never accepted as success. The restored target must
match the source fingerprint across the full public-table set and Flyway
metadata.

## Application startup verification

After persistence comparison, the current PayFlow JAR is built by default and
started against the isolated restored database.

Background mail/outbox/Kafka consumers and generalized abuse protection are
disabled for this isolated startup check. The source database is not used.

Startup passes only when PayFlow reaches:

```text
GET /api/v1/system/health
HTTP 200
```

The temporary PayFlow process and restore container are then terminated.

## Generated evidence and privacy

Runtime evidence is written below the ignored rehearsal directory. Sanitized
evidence includes only bounded operational facts such as:

- Git branch and HEAD;
- PostgreSQL version;
- backup byte size and SHA-256;
- public-table count;
- Flyway successful-row count/latest version/metadata digest;
- representative table row counts;
- PASS/FAIL state for restore, Flyway comparison, application startup, and
  health.

The evidence must not contain:

- row values or unnecessary personal data;
- passwords or password hashes;
- refresh credentials or credential digests;
- JWTs, MFA secrets, recovery codes, or step-up grants;
- protected mail content;
- random target-database passwords.

## Failure behavior

The rehearsal fails closed when:

- the Git tree is dirty or HEAD is detached;
- Docker is unavailable;
- source selection is ambiguous;
- PostgreSQL is not on the expected major line;
- the source application is still running;
- source Flyway is stale or ahead of the checked-out migration set;
- representative persistence is missing;
- source state changes across the backup window;
- `pg_dump`, `docker cp`, or `pg_restore` fails;
- restored table/Flyway fingerprints differ;
- the application JAR cannot be built;
- PayFlow exits during isolated startup;
- restored-database health does not reach HTTP 200.

The script does not compensate for these failures by deleting the source,
resetting its schema, restoring over it, or removing its persistent volume.

## Options

`-KeepDump`
: Keep the ignored custom-format dump after the rehearsal for explicit local
inspection. The default removes it.

`-SkipPackage`
: Reuse the existing JAR resolved dynamically as
`target/<artifactId>-<project.version>.jar`. On the release-preparation candidate
this resolves to `target/payflow-1.0.0.jar`; the script fails if the
resolved artifact is absent.

`-ComposeProject`, `-PostgresService`, `-AppService`, `-SourceDatabase`,
`-SourceUser`
: Override the explicit local source selectors. These values never turn the
isolated target into the source.

## Recovery limitations

This rehearsal proves a bounded local backup/restore path. It does **not**
provide or claim:

- point-in-time recovery or WAL archival;
- down-migration support;
- cloud-provider backup integration;
- automated retention/rotation policy;
- production RPO/RTO;
- production disaster-recovery certification;
- logical row-by-row content checksums.

Row-count plus Flyway metadata verification is intended to detect an
empty/partial restore while avoiding exposure of sensitive row contents.

## Current v1.0.0 CP5 rehearsal evidence

On 2026-08-28, the recovery tooling was rehearsed from exact candidate
`33f5ce5e37ec0e947ad21d7fd415409b8773063b` after the restored-database
launcher was aligned to `System.Diagnostics.Process`.

The reviewed checkpoint proved:

- PostgreSQL `17.11`;
- Flyway latest `V24` with `24` successful versioned rows;
- `19` public tables;
- source fingerprint unchanged across the backup window;
- custom-format backup and clean isolated restore: PASS;
- complete public-table row-count and Flyway metadata comparison: PASS;
- PayFlow startup against the restored database: HTTP `200`;
- reused candidate JAR SHA-256:
  `30898695e9647c98a678551950131d6c22ec882d35d220ec19b606827b8fa0f7`;
- sanitized rehearsal evidence SHA-256:
  `6757de9340dd02515ce11d2ec83685ea397c9f4a2a11341616008499cccad330`;
- isolated restore target removed after the rehearsal; and
- the source application restored to healthy HTTP `200` service afterward.

This remains bounded developer/release-candidate evidence. It is not a
production disaster-recovery, RPO, or RTO certification.

## Historical v0.16.0 rehearsal evidence

On 2026-08-20, before committing this operational contract, the procedure was
rehearsed from exact repository baseline
`d5c888b41082af7e48d964b4f403719ce6e031a6`.

The successful final rehearsal recorded:

- PostgreSQL `17.11`;
- Flyway latest `V24`;
- `19` public tables, including `flyway_schema_history`;
- custom-format backup: PASS;
- clean isolated restore: PASS;
- complete public-table row-count comparison: PASS;
- Flyway metadata comparison: PASS;
- PayFlow startup against the restored database: PASS;
- `/api/v1/system/health`: HTTP `200`;
- repository tree: CLEAN;
- sanitized external evidence SHA-256:
  `644a8d8573a7efe224d9e4e03396f1818ed0cf92d9d64a202fa46e34fa168b4f`.

The developer's persistent local volume was initially at Flyway V6. A safety
backup was taken and that local source was upgraded to V24 as environment
preparation before the successful final backup/restore probe. That preparation
was **not** the v0.16.0 Increment 3 clean-install/previous-release upgrade
rehearsal and did not by itself mark Increment 3 complete. Increment 3 is
verified separately by the Flyway clean-install / upgrade rehearsal.
