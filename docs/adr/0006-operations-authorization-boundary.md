# ADR 0006: Operations Authorization Boundary

- Status: Accepted
- Date: 2026-07-22

## Context

PayFlow now supports durable and controlled Kafka dead-letter
replay. The replay application use case is intentionally not exposed
through an HTTP endpoint because operational capabilities require a
dedicated authorization boundary.

The current authentication model issues RSA-signed JWT access tokens.
Each token contains:

- `sub`: the authenticated user identifier
- `email`: the authenticated user's email address
- `role`: the user's PayFlow domain role

The supported domain roles are `USER` and `ADMIN`. Public registration
always creates a `USER`.

Spring Security currently authenticates JWTs but does not map the
PayFlow `role` claim to application authorities. Customer-facing
endpoints require authentication only.

Operational capabilities are more sensitive than customer operations.
They must not rely on request-controlled values or on broad implicit
role conventions.

## Decision

PayFlow will introduce an explicit operations authority:

```text
PAYFLOW_OPERATIONS
```

A dedicated JWT authentication converter will inspect the trusted
`role` claim after token signature, expiry, and issuer validation.

The authority mapping will be:

- `ADMIN` maps to `PAYFLOW_OPERATIONS`
- `USER` receives no operations authority
- a missing role receives no operations authority
- a blank role receives no operations authority
- a non-string role receives no operations authority
- an unknown role receives no operations authority

The mapping is exact and fail-closed. The converter will not infer
operational authority from:

- HTTP headers
- query parameters
- path variables
- request bodies
- email addresses
- request-provided user identifiers
- OAuth scopes not issued and defined by PayFlow

Operational HTTP endpoints will use the namespace
`/api/v1/operations/**`.

The Spring Security filter chain will require
`PAYFLOW_OPERATIONS` for that namespace.

Customer-facing endpoints will retain their current authenticated-user
behavior.

All unmatched endpoints will continue to be denied by default.

## Authentication and authorization behavior

Requests without a valid access token will receive HTTP 401.

Requests with a valid access token but without
`PAYFLOW_OPERATIONS` will receive HTTP 403.

Requests with the required authority may proceed to a protected
operations controller.

Authorization must occur before the controller or application use case
is invoked.

## User provisioning

Public registration will continue to create only `USER` accounts.

This decision does not introduce a public role-assignment or
privilege-elevation endpoint.

Operational users must be provisioned through a trusted administrative
process outside the public registration flow.

## Rationale

Using the existing `ADMIN` domain role avoids introducing an additional
database role solely for operational HTTP delivery.

Mapping `ADMIN` to the narrower `PAYFLOW_OPERATIONS` authority keeps the
HTTP authorization boundary independent from Spring Security's
`ROLE_` prefix convention and from direct domain-role checks.

An explicit converter is preferred over Spring Security's default
scope mapping because PayFlow issues a single `role` claim rather than
OAuth scopes, and fail-closed claim handling is required.

A dedicated operations namespace makes the sensitive API surface easy
to identify, secure, test, document, observe, and audit.

## Consequences

### Positive

- operational endpoints are denied by default
- public users cannot self-assign operational access
- authorization depends only on trusted token claims
- customer endpoint behavior remains backward compatible
- future operational authorities can be introduced independently
- controllers and application services remain independent from
  Spring Security types

### Negative

- a custom JWT authentication converter must be maintained
- trusted administrative provisioning is required for `ADMIN` users
- role changes take effect only after a new access token is issued
- an `ADMIN` token grants the complete operations authority until more
  granular authorities are introduced

## Alternatives considered

### Use `hasRole("ADMIN")` directly

Rejected because it couples the operations API to Spring Security's
`ROLE_` naming convention and directly to the domain role name.

### Introduce an `OPERATIONS` user role

Rejected for the current scope because the existing `ADMIN` role and
database constraint support trusted operator provisioning.

A separate operator role may be introduced later if administrator and
operator identities require independent permissions.

### Accept an operations flag from the request

Rejected because request-controlled authorization data is not trusted.

### Expose replay without an authorization boundary

Rejected because dead-letter replay can cause externally visible and
duplicate Kafka message delivery.

## Verification

The implementation must prove:

- `ADMIN` maps to `PAYFLOW_OPERATIONS`
- `USER` does not receive the operations authority
- missing, blank, non-string, and unknown role claims fail closed
- anonymous operations requests return HTTP 401
- authenticated non-operator requests return HTTP 403
- authorized operator requests reach the protected controller
- authorization failures do not invoke controller collaborators
- customer endpoints retain their existing authenticated behavior
- unknown endpoints remain denied
- the full Maven verification build succeeds
