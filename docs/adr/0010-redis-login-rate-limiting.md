# ADR 0010: Use Redis-backed login rate limiting

- Status: Accepted
- Date: 2026-07-30

## Context

PayFlow exposes the public authentication endpoint:

```text
POST /api/v1/auth/login
```

Login performs account lookup and BCrypt password verification. Repeated requests
can therefore be used for password brute force, credential stuffing, targeted
account abuse, and resource exhaustion.

PayFlow already includes Spring Data Redis and a Redis service in the local
development environment. The protection must work consistently across
application instances, remain atomic under concurrency, avoid storing raw
identifiers, and preserve the existing invalid-credentials contract.

## Decision

PayFlow will protect login with a Redis-backed fixed-window rate limiter.

Two independent dimensions will be evaluated before account lookup and password
verification:

1. normalized login identity
2. network peer address

Both dimensions must admit the request.

Initial defaults:

- identity limit: 5 attempts per 15-minute window
- client-address limit: 20 attempts per 15-minute window
- the sixth identity attempt is rejected
- the twenty-first client-address attempt is rejected

Limits and durations are configuration properties.

## Atomic Redis operation

One Lua script will atomically:

1. increment the identity counter
2. set its expiration when first created
3. increment the client-address counter
4. set its expiration when first created
5. read remaining TTL values
6. return admission state, blocking dimensions, and retry delay

The adapter will execute one singleton `DefaultRedisScript` through
`StringRedisTemplate`.

This prevents:

- increments without expiration
- partial updates between dimensions
- threshold bypass through concurrent requests
- inconsistent retry-delay calculation

Denied attempts do not extend the active fixed window.

## Counter lifecycle

Failed authentication retains both counters.

Successful authentication deletes only the identity counter. The client-address
counter remains so one source cannot avoid network-level protection by
successfully authenticating one account.

A request rejected by the limiter does not perform:

- user lookup
- BCrypt password verification
- token generation
- refresh-session persistence

## Redis keys and sensitive data

Raw email addresses and raw client addresses must not appear in Redis keys.

The Redis adapter will:

1. normalize the email using the existing `EmailAddress` value object
2. normalize the network peer address
3. calculate SHA-256 for each normalized value
4. encode the digest as lowercase hexadecimal
5. place only the digest in the key

Key namespaces:

```text
payflow:security:login:identity:<sha256>
payflow:security:login:client:<sha256>
```

Redis keys and values must not contain:

- passwords or password hashes
- access or refresh tokens
- raw email addresses
- raw client addresses

Digests must not be exposed through responses, logs, metrics, traces, or audit
records.

## Client-address trust boundary

The first implementation will use `HttpServletRequest.getRemoteAddr()`.

Application code will not directly trust:

```text
X-Forwarded-For
Forwarded
X-Real-IP
```

Forwarded-header processing requires a separate production deployment decision
with an explicit trusted-proxy boundary.

## HTTP contract

When either dimension is blocked, PayFlow returns:

```text
HTTP 429 Too Many Requests
```

The centralized API error contains:

```text
code: LOGIN_RATE_LIMIT_EXCEEDED
message: Too many login attempts. Try again later.
path: /api/v1/auth/login
```

The response includes:

```text
Retry-After: <whole seconds>
```

The delay is the longest remaining TTL among blocked dimensions, with a minimum
of one second.

The response does not disclose whether the account exists, which dimension was
blocked, current counts, configured limits, or Redis keys.

## Redis outage behavior

The default behavior is fail-closed.

When Redis cannot provide a reliable admission decision, PayFlow skips password
verification and returns:

```text
HTTP 503 Service Unavailable
```

The API error code is:

```text
LOGIN_RATE_LIMIT_UNAVAILABLE
```

The response does not expose Redis commands, hosts, topology, exception
messages, or connection details.

An explicitly configured fail-open production mode is outside this increment.

## Metrics and security events

The implementation will publish bounded Micrometer counters:

```text
payflow.auth.login.rate_limit.decisions
payflow.auth.login.rate_limit.redis.failures
```

Allowed tags are limited to bounded values such as:

```text
outcome=allowed|blocked
dimension=none|identity|client|both
operation=evaluate|reset
```

Metrics and security events must not contain email addresses, client addresses,
digests, user IDs, passwords, credentials, Redis keys, or exception messages.

## Application boundaries

The user module defines a framework-independent output port for login
rate-limiting decisions.

The Redis adapter owns:

- Redis key construction
- SHA-256 identifier hashing
- Lua execution
- result parsing
- Redis exception translation

Authentication orchestration owns:

- identity normalization
- evaluation before authentication
- identity-counter reset after successful authentication
- translation of decisions into application exceptions

The web adapter owns:

- transport-peer extraction
- HTTP 429 and 503 responses
- `Retry-After`
- OpenAPI documentation

The application layer does not depend directly on servlet, Redis, Lua, or
Micrometer types.

## Configuration

Initial configuration namespace:

```yaml
payflow:
  security:
    login-rate-limit:
      enabled: true
      window: 15m
      identity-limit: 5
      client-limit: 20
```

Production configuration enables the limiter.

General tests disable it so unrelated authentication tests do not silently
depend on Redis. Dedicated integration tests enable it and use an isolated Redis
Testcontainer.

## Testing requirements

The increment must cover:

- configuration validation
- deterministic hashing and raw-identifier exclusion
- first-attempt expiration
- atomic identity and client increments
- exact threshold behavior
- identity-only, client-only, and combined blocking
- retry-delay calculation
- successful-login identity reset
- failed-login counter retention
- concurrent admission behavior
- automatic expiration
- Redis failure translation
- HTTP 429 and `Retry-After`
- HTTP 503 fail-closed behavior
- OpenAPI documentation
- absence of credential data in responses
- complete Maven verification

## Consequences

Benefits:

- limits are shared across application instances
- decisions are atomic
- counters expire automatically
- blocked requests avoid expensive password verification
- raw identities are not stored
- metrics remain low-cardinality
- Redis outage behavior is deterministic

Costs:

- Redis becomes required for login availability
- fixed windows permit boundary bursts
- identity limits may temporarily affect a legitimate user
- a reverse proxy may appear as the peer until trusted-proxy handling is defined
- additional integration and concurrency tests are required

The fixed-window trade-off is accepted for PayFlow v0.9.0. Sliding-window or
token-bucket algorithms may be considered later if production traffic requires
smoother admission behavior.
