# ADR 0001: Start as a modular monolith

- Status: Accepted
- Date: 2026-07-12

## Context

The system needs clear business boundaries, transactional consistency, meaningful tests, and a development model that remains manageable for a portfolio project.

## Decision

PayFlow starts as a package-by-feature modular monolith. Each feature may contain domain, application, and adapter packages. Dependencies point inward through ports.

## Consequences

- Local transactions remain straightforward.
- Deployment and debugging stay simple.
- Module boundaries can be tested and documented.
- A module such as notification can later be extracted when operational reasons justify it.
- We deliberately avoid premature microservices.
