# Clean-environment release rehearsal

This procedure proves a reviewed PayFlow commit from a fresh isolated checkout
without publishing a release.

## Purpose

The rehearsal verifies that release readiness does not depend on developer-local
`target/`, `.runtime/`, host OpenSSL, conventional developer service ports, or
other hidden generated state.

The executable entry point is:

```powershell
$Head = git rev-parse HEAD

powershell.exe `
  -NoProfile `
  -ExecutionPolicy Bypass `
  -File scripts/release/verify-clean-environment-rehearsal.ps1 `
  -ExpectedHead $Head
```

Run it only from a clean reviewed commit. The script rejects a dirty source
working tree or an unexpected `HEAD`.

## What the rehearsal proves

The script creates a detached Git worktree for the exact supplied commit and
requires `target/` and `.runtime/` to be absent before verification begins.

It then verifies:

- Java 21 and the committed Maven Wrapper 3.9.16 contract;
- the Maven Wrapper distribution SHA-256 pin and executable `mvnw` Git mode;
- immutable Docker builder/runtime digest pins;
- release workflow use of `./mvnw`, executable JAR/checksum assets, and the
  GitHub Release command contract;
- complete Maven verification with zero failures, errors, or skipped tests;
- executable snapshot JAR creation and a sha256sum-compatible checksum record;
- the committed Gitleaks baseline;
- the committed vulnerability review and local SBOM/provenance evidence with no
  unresolved Critical/High blocker and no suppression/retuning;
- required production configuration fail-fast before containers are created;
- production-profile Docker startup, health, correlation ID, and structured
  request-completion log behavior.

Test totals are read from Surefire XML reports rather than hard-coded, so the
rehearsal does not snapshot a volatile test count.

## Host-independent Docker behavior

The production container runs as UID 10001. Synthetic JWT key material is
therefore generated inside an isolated Docker-managed Linux volume, owned by
`10001:10001`, with private/public modes `0400` and `0444`. A UID 10001 read
probe must pass before the application is started.

The rehearsal does not require host OpenSSL.

Dockerfile digest pins are parsed as logical lines rather than with
newline-sensitive multiline matching. This keeps the rehearsal portable across
LF and CRLF checkouts, including Windows repositories where Dockerfile is
covered by the generic text rule.

PostgreSQL, Redis, Kafka, and Mailpit do not publish their conventional
developer host ports during the rehearsal. Only the application is published,
on a Docker-selected loopback port used for the health probe. Existing local
developer services therefore do not need to be stopped.

Required encryption values are generated at runtime and are never written to
the safe evidence JSON.

## Evidence

Generated evidence is local-only under:

```text
.runtime/release-rehearsal/<commit>/
```

The safe JSON records commit/tree identity, toolchain versions, test summary,
artifact/checksum hashes, reviewed security-evidence hashes, fail-fast outcome,
and Docker smoke outcome.

The safe evidence intentionally does not record:

- generated encryption values;
- JWT private keys;
- raw application logs;
- machine-specific worktree paths;
- credentials, tokens, or personal data.

Security scripts may create their own ignored `.runtime/security/...` evidence
inside the isolated worktree while the rehearsal runs. The temporary worktree
and JWT volume are removed during cleanup.

## Evidence boundaries

A local rehearsal PASS is not a GitHub-hosted CI result and is not release
publication.

It does not claim:

- artifact signing;
- SLSA provenance;
- reproducible builds;
- provenance attestation;
- production or regulatory certification;
- production deployment;
- real-money readiness.

The rehearsal does not create a Git tag or GitHub Release.

For a merge decision, the local exact-head rehearsal is supplementary to the
repository's GitHub-hosted merge gates. The final reviewed PR head must still
pass CI and Docker Smoke before merge.
