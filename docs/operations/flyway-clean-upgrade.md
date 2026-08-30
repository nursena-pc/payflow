# Flyway Clean-Install and Upgrade Rehearsal

Historical rehearsal issue: [#175](https://github.com/nursena-pc/payflow/issues/175)
Current v1.0.0 CP5 review: [#199](https://github.com/nursena-pc/payflow/issues/199)

PayFlow v1.0.0 release-candidate CP5 reuses and revalidates this local rehearsal
to prove two separate forward-migration paths on PostgreSQL 17:

1. an empty database reaches the complete current V1 through V24 schema; and
2. the approved immutable `v0.13.0` / V17 release schema and representative
   synthetic data upgrade through V18 through V24 without hidden historical
   drift.

The committed entry point is:

```powershell
.\scripts\operations\flyway-clean-upgrade-rehearsal.ps1
```

This is developer/stabilization evidence. It is not production migration,
rollback, disaster-recovery, RPO, or RTO certification.

## Approved previous-release baseline

The reviewed previous-release source is immutable tag `v0.13.0`, whose release
commit is:

```text
726f631a0de800870813ccb0c00b2676eb5d172b
```

That release carries Flyway V1 through V17. The current v1.0.0 release-candidate line carries V1
through V24. Inventory performed for issue #175 proved that the current V1
through V17 migration blobs are identical to the immutable v0.13.0 blobs, so
the upgrade delta is exactly V18 through V24.

`v0.14.0` and `v0.15.0` are deliberately not used as schema-upgrade sources:
both already carry the same V24 migration tree as the current line and
therefore would not exercise a real schema upgrade.

The rehearsal refuses an unexpected `v0.13.0` tag target or historical V1
through V17 blob drift.

## Prerequisites and isolation

Before running:

- Docker must be available.
- `JAVA_HOME` must point to the Java 21 JDK used by PayFlow, and the Maven
  wrapper must be available.
- immutable tag `v0.13.0` must exist locally at the reviewed commit.
- HEAD must be attached to a branch and the Git working tree must be clean.
- PostgreSQL 17 container images must be available.

The procedure never uses the developer's persistent PayFlow database. Application
startup resolves `JAVA_HOME/bin/java.exe`, verifies that it is Java 21, and
never falls back to an unrelated bare `java` earlier on `PATH`.

The procedure starts two separate loopback-only, ephemeral
`postgres:17-alpine` containers with random process-local passwords:

- one clean-install target;
- one previous-release upgrade target.

The temporary v0.13.0 source checkout is a detached Git worktree outside the
main working tree and is removed after the rehearsal. Runtime logs, synthetic
SQL, and sanitized evidence live below:

```text
.runtime/flyway-rehearsal/<timestamp>/
```

`.runtime/` is ignored by Git.

## Clean-install proof

The current `1.0.0` release-preparation application is built and started against an empty
PostgreSQL 17 database. Application startup owns normal Flyway execution;
Hibernate remains `ddl-auto: validate`.

Success requires:

- exactly 24 successful versioned Flyway rows;
- ordered V1 through V24 versions;
- script names matching the checked-in migration files;
- the complete current 19-table public schema, including
  `flyway_schema_history`;
- the current refresh-family and account-security check constraints;
- successful PayFlow startup; and
- `GET /api/v1/system/health` returning HTTP 200.

No `flyway repair`, baseline shortcut, schema import, dump restore, or manual DDL
is used to make the clean installation pass.

## Previous-release V17 baseline

The rehearsal creates the upgrade source by building the immutable v0.13.0
source in its detached worktree and starting that JAR against a second empty
PostgreSQL 17 database.

That startup must produce exactly V1 through V17 and the expected V17 public
table set.

Only after the immutable release baseline is proven does the script insert
deterministic synthetic rehearsal state.

## Representative synthetic data boundary

All 13 non-Flyway V17 data tables are deliberately non-empty before upgrade:

- `users`
- `wallets`
- `payment_transactions`
- `ledger_entries`
- `outbox_events`
- `processed_kafka_events`
- `transfer_completed_event_audits`
- `kafka_dead_letter_records`
- `kafka_dead_letter_command_audits`
- `refresh_token_families`
- `refresh_token_records`
- `account_action_credentials`
- `mail_outbox_messages`

The seed contains only fixed synthetic identifiers and `example.invalid` /
`payflow.invalid` values. It is not copied from a developer or production
database.

Before the current application is allowed to run V18 through V24, each table
receives a deterministic fingerprint consisting of row count plus an MD5 digest
of sorted PostgreSQL `to_jsonb(row)::text` values.

After the upgrade, every one of those 13 table fingerprints must match exactly.
This is stronger than a count-only check and is appropriate here because the
input is deliberately synthetic and contains no real credentials or personal
data.

Fingerprint digests, not row values, are written to sanitized evidence.

## Upgrade and current-schema proof

The current v1.0.0 release-candidate application then starts against the V17 database and applies
only V18 through V24.

Success requires:

- the final Flyway sequence to match checked-in V1 through V24;
- the deterministic V1 through V17 Flyway metadata digest to remain unchanged;
- all 13 V17 table content fingerprints to remain unchanged;
- V18 through V22 current MFA/account-security tables to exist;
- the V23 refresh-family constraint to accept `MFA_DISABLED`;
- the V24 account-security audit constraint to accept
  `RECOVERY_CODES_ROTATED`;
- the V23/V24 behavior probe to roll back without persisting rehearsal rows;
- successful current PayFlow startup; and
- `/api/v1/system/health` to return HTTP 200.

## Failure and drift handling

The rehearsal fails rather than repairing history when it sees:

- a dirty or detached Git state;
- missing `JAVA_HOME`, a missing `JAVA_HOME/bin/java.exe`, or a `JAVA_HOME`
  runtime whose major version is not Java 21;
- an unexpected immutable v0.13.0 tag target;
- missing, extra, duplicated, renamed, or failed Flyway history;
- historical V1 through V17 blob drift;
- a migration sequence other than current V1 through V24;
- a v0.13.0 baseline other than V1 through V17;
- a missing current table or required constraint value;
- an empty representative V17 data table;
- a changed V17 row count or row-content fingerprint after upgrade;
- failed PostgreSQL startup;
- failed current or previous-release application startup; or
- health that does not reach HTTP 200.

The script does not contain `flyway repair`, `git reset --hard`, database-volume
deletion, source-database overwrite, or an automated down-migration.

## Recovery and rollback boundary

Flyway versioned migrations are treated as forward migrations. PayFlow does not
claim automated down-migration support.

If an operational upgrade must be abandoned and returning to a pre-upgrade
database state is required, use the separately reviewed
[PostgreSQL backup/restore procedure](postgresql-backup-restore.md) with an
appropriate pre-upgrade backup. Increment 3 does not silently couple database
backup creation to migration execution.

This local rehearsal does not establish point-in-time recovery, WAL archival,
production rollback automation, production RPO/RTO, or disaster-recovery
certification.

## Evidence and privacy

Generated evidence contains bounded operational facts only:

- branch and exact HEAD;
- immutable previous-release tag/commit;
- PostgreSQL image line;
- clean-install and upgrade PASS state;
- migration ranges;
- historical-drift result;
- table counts and content digests for synthetic V17 state;
- constraint checks; and
- application health results.

It must not contain real email addresses, passwords, password hashes from real
users, refresh credentials, JWTs, MFA secrets, recovery codes, step-up grants,
protected mail content, production payloads, random target-database passwords,
or database dumps.

## Reviewed local proof

Before this contract was committed, the exact issue #175 branch baseline
`08670c89a5a673cecaa87dcc6f48871cbcc504d8` was rehearsed successfully on
2026-08-20.

The successful probe proved:

- clean PostgreSQL 17 install V1 through V24: PASS;
- clean-installed application health: HTTP 200;
- approved upgrade source: immutable v0.13.0 / V17;
- exact upgrade delta V18 through V24: PASS;
- historical migration drift: zero;
- all 13 V17 data tables seeded with synthetic state;
- all 13 V17 row-content fingerprints preserved;
- upgraded application health: HTTP 200;
- V23 `MFA_DISABLED` constraint behavior: PASS;
- V24 `RECOVERY_CODES_ROTATED` constraint behavior: PASS; and
- repository tree remained clean.

Sanitized external probe evidence SHA-256:

```text
7709c3e1f56d8d5128cbcd98318b5a1d0b8aaab05d1ce41196646dd2ce7d585e
```

The committed script is rerun from its own reviewed commit before the branch is
pushed for PR review.
