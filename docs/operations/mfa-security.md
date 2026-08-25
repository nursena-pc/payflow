# MFA Security Operations

This guide describes the MFA and purpose-bound step-up operations retained by
the PayFlow `1.0.0-SNAPSHOT` release-candidate line. It does not add
authentication methods or change the historical v0.14.0 security invariants.

## Production prerequisites

- configure dedicated MFA AES-256-GCM key material independently from JWT and mail keys
- keep key material outside source control and container images
- apply Flyway migrations through V24 before enabling production MFA traffic
- synchronize application-node clocks for bounded TOTP verification

## Enrollment

Enrollment returns the Base32 secret and provisioning URI once. Operators must
never copy these values into tickets, logs, dashboards, traces, or persistent
diagnostics.

Pending enrollment expires according to configuration. Cancellation deletes
only pending authenticator state.

## Login challenges

Enabled MFA creates no access or refresh credential until the challenge is
consumed successfully. Repeated invalid attempts, replay, expiry, exhaustion,
and supersession use the same public failure boundary.

Investigations should use bounded error codes and correlation identifiers,
never challenge tokens, TOTP proofs, recovery codes, or digests.

## Recovery codes

Recovery codes are shown once at activation or rotation. Only digests are
stored. Rotation invalidates the complete previous set atomically.

Operators cannot retrieve plaintext recovery codes. Users who lose every
factor require a separately governed account-recovery process; database edits
must not be used to bypass MFA.

## Step-up operations

MFA disable and recovery-code rotation require separate exact-purpose grants.
Grants are subject-bound, short-lived, single-use, and superseding.

A grant created for one operation must never authorize another operation.

## MFA disable

Successful disablement removes authenticator and recovery-code state, revokes
active refresh-token families, and appends account-security audit evidence in
one transaction.

Already-issued access JWTs retain only their configured residual lifetime.

## Incident handling

- treat suspected MFA key disclosure as a security incident
- stop affected deployments before rotating configured key material
- preserve credential-free audit and correlation evidence
- do not export database rows containing protected bytes or digests into tickets
- verify PostgreSQL, Redis, and application health before restoring traffic
- run the full Maven and Docker smoke suites after remediation

## Explicit limitation

Active-authenticator replacement remains outside the v1.0.0 release-candidate scope. It requires a
separately reviewed two-stage lifecycle that cannot silently destroy the only
working factor before replacement confirmation.
