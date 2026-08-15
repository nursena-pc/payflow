# Contributing

## Branching

- `main`: protected, stable, and releasable integration history
- `feat/<short-name>`: new product or technical capability
- `fix/<short-name>`: defect correction
- `docs/<short-name>`: documentation-only change
- `chore/<short-name>`: maintenance, tooling, or repository configuration
- `release/<version>-<purpose>`: bounded release preparation and finalization

Branches must be created from the latest `main` branch. Related issues are linked from the pull request rather than encoded into the branch name. Release branches are used only for bounded release-preparation work and are deleted after the release PR is merged and verified.

## Commit convention

Use Conventional Commits:

- `feat(wallet): add wallet opening use case`
- `fix(transaction): prevent duplicate idempotency key processing`
- `test(ledger): cover unbalanced entry rejection`
- `docs(adr): document outbox decision`

## Pull request rules

1. Keep pull requests focused and reasonably small.
2. Link the related issue and state explicit non-goals where scope could expand.
3. Add tests for business behavior and executable contracts where applicable.
4. Explain database, security, concurrency, migration, and observability implications when relevant.
5. Require protected CI checks to pass before merging.
6. Verify that the reviewed PR HEAD matches the expected commit before merge.
7. Use merge commits to preserve pull-request and release provenance; do not rewrite published history.
8. Delete merged feature and release branches after merge-state and target-branch verification.
