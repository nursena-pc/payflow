<!-- payflow-release-v0.8.0 -->
# Changelog

All notable PayFlow changes are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and PayFlow uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.8.0] - 2026-07-29

### Added

- Durable opaque refresh-token sessions backed by PostgreSQL.
- SHA-256 digest-only refresh credential persistence and secure token generation.
- Refresh credentials issued during successful login.
- Single-use refresh-token rotation with access and refresh credential renewal.
- Refresh-token family revocation when consumed-token reuse is detected.
- Public `POST /api/v1/auth/logout` for current-session family revocation.
- OpenAPI contracts and security configuration for refresh and logout operations.
- PostgreSQL integration coverage for persistence, locking, rollback, and concurrency.

### Security

- Raw refresh credentials and stored digests remain redacted from public representations.
- Refresh rotation serializes on pessimistic record and family locks.
- Reuse detection invalidates every credential in the affected family.
- Logout returns a state-independent `204 No Content` response for validly shaped credentials.
- Logout-versus-rotation races preserve one-successor and first-revocation-reason guarantees.
- Persistence failures roll back session mutations instead of returning false success.

### Database

- Added `V14__create_refresh_token_sessions.sql` for refresh-token families and records.

### Completed milestone work

- [#80](https://github.com/nursena-pc/payflow/issues/80) chore: start v0.8.0 development
- [#82](https://github.com/nursena-pc/payflow/issues/82) feat(auth): add secure refresh-token cryptography adapters
- [#84](https://github.com/nursena-pc/payflow/issues/84) feat(auth): issue refresh credentials on login
- [#86](https://github.com/nursena-pc/payflow/issues/86) feat(auth): rotate refresh credentials
- [#88](https://github.com/nursena-pc/payflow/issues/88) feat(auth): revoke refresh-token family on reuse
- [#90](https://github.com/nursena-pc/payflow/issues/90) feat(auth): revoke current refresh-token session on logout

[0.8.0]: https://github.com/nursena-pc/payflow/compare/v0.7.0...v0.8.0
