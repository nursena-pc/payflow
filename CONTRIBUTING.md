# Contributing

## Branching

- `main`: stable, releasable history
- `develop`: integration branch while the project is under active development
- `feature/<issue-number>-short-name`
- `fix/<issue-number>-short-name`

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
