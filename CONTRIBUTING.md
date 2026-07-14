# Contributing

## Branching

- `main`: stable and releasable history
- `develop`: integration branch while the project is under active development
- `feat/<short-name>`: new product or technical capability
- `fix/<short-name>`: defect correction
- `docs/<short-name>`: documentation-only change
- `chore/<short-name>`: maintenance, tooling, or repository configuration

Branches must be created from the latest `develop` branch. Related issues are linked from the pull request rather than encoded into the branch name.
## Commit convention

Use Conventional Commits:

- `feat(wallet): add wallet opening use case`
- `fix(transaction): prevent duplicate idempotency key processing`
- `test(ledger): cover unbalanced entry rejection`
- `docs(adr): document outbox decision`

## Pull request rules

1. Keep pull requests focused and reasonably small.
2. Link the related issue.
3. Add tests for business behavior.
4. Explain database, security, and concurrency implications.
5. Require CI to pass before merging.
6. Prefer squash merge to keep the main history readable.
