# Redis-Backed Login Rate Limiting

PayFlow protects `POST /api/v1/auth/login` with distributed, fixed-window
counters stored in Redis. The protection is intentionally limited to login;
refresh, logout, and authenticated business endpoints are outside this policy.

## Security goals

The implementation is designed to:

- reduce automated brute-force and credential-stuffing attempts
- apply the same policy across multiple application instances
- avoid revealing whether a login identity exists
- keep raw emails, passwords, tokens, and client addresses out of Redis keys,
  logs, and metric labels
- fail closed when the protection decision cannot be made safely

## Policy

Two counters are evaluated atomically for every login attempt:

| Dimension | Default threshold | Key input |
|---|---:|---|
| Identity | 5 attempts per 15 minutes | Normalized email address |
| Client | 20 attempts per 15 minutes | Normalized effective client address |

The first five attempts for one identity are evaluated normally. The sixth
attempt in the same window is blocked. The first twenty attempts for one client
are evaluated normally; the twenty-first is blocked.

A successful login deletes only the identity counter. The client counter
remains until its original fixed-window expiration. This prevents one valid
credential from erasing the broader client-abuse signal.

## Redis model

Identity and client values are normalized and SHA-256 hashed before they are
placed in Redis keys. The Lua script performs the following work atomically:

1. increment both counters
2. assign expiration when a key is created
3. preserve the original fixed-window expiration on later attempts
4. determine the blocked dimension
5. return the longest relevant TTL as `Retry-After`

PostgreSQL remains the system of record. Redis stores only short-lived abuse
control counters.

## HTTP contracts

### Below threshold

Invalid credentials continue to return the generic response:

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json
```

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Email or password is incorrect."
}
```

The response does not disclose whether the identity exists.

### Threshold exceeded

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 734
Content-Type: application/json
```

```json
{
  "code": "LOGIN_RATE_LIMIT_EXCEEDED",
  "message": "Too many login attempts. Try again later.",
  "violations": []
}
```

`Retry-After` is expressed as positive whole seconds and is derived from the
active Redis TTL.

### Redis unavailable

A Redis command failure produces a fail-closed response:

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
```

```json
{
  "code": "LOGIN_RATE_LIMIT_UNAVAILABLE",
  "message": "Login protection is temporarily unavailable.",
  "violations": []
}
```

Authentication does not continue when the limiter cannot evaluate or reset its
state safely.

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `LOGIN_RATE_LIMIT_ENABLED` | `true` | Enables login protection |
| `LOGIN_RATE_LIMIT_WINDOW` | `15m` | Fixed-window duration |
| `LOGIN_RATE_LIMIT_IDENTITY_LIMIT` | `5` | Allowed attempts per identity |
| `LOGIN_RATE_LIMIT_CLIENT_LIMIT` | `20` | Allowed attempts per client |
| `TRUSTED_PROXY_CIDRS` | empty | Comma-separated IPv4 or IPv6 CIDRs allowed to supply forwarding metadata |
| `FORWARDED_HEADER_MAX_LENGTH` | `4096` | Maximum combined selected forwarding-header length |
| `FORWARDED_MAX_HOPS` | `16` | Maximum accepted forwarding-chain elements |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |

Threshold and window changes should be reviewed as security-policy changes.
Avoid values that create an easy denial-of-service path for shared networks.

## Client-address trust boundary

The direct servlet peer is the authority that decides whether forwarding
metadata may be considered. An empty `TRUSTED_PROXY_CIDRS` value is the secure
default: every request uses the direct peer and all forwarding headers are
ignored.

When the direct peer belongs to a configured trusted-proxy CIDR, PayFlow applies
this deterministic policy:

1. use `Forwarded` when it is present
2. otherwise use `X-Forwarded-For`
3. never fall back to `X-Forwarded-For` after a present `Forwarded` value fails validation
4. parse the chain from right to left
5. skip configured trusted proxy hops
6. select the first untrusted address as the effective client
7. when every supplied hop is trusted, select the leftmost address

Only literal IPv4 and IPv6 values are accepted. Hostnames are never resolved.
`unknown`, obfuscated identifiers, malformed quoting, invalid ports, oversized
headers, and excessive hop counts fail safely to the direct peer. The resolver
also falls back to the direct peer when no supported forwarding header is
present.

The accepted configuration rejects host-bit CIDRs, duplicates, unrestricted
`/0` networks, more than 64 trusted networks, header limits outside
`256..16384`, and hop limits outside `1..64`.

### Reverse-proxy example

For a Docker network where only the reverse-proxy subnet is trusted:

```text
TRUSTED_PROXY_CIDRS=172.20.0.0/16
FORWARDED_HEADER_MAX_LENGTH=4096
FORWARDED_MAX_HOPS=16
```

A minimal Nginx location can supply one supported header format:

```nginx
location / {
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_pass http://payflow:8080;
}
```

The trusted CIDR must identify the actual network peer that connects to
PayFlow, not the public client network. Do not configure public internet ranges,
`0.0.0.0/0`, or `::/0`. If an upstream already supplies `Forwarded`, remember
that it takes precedence over `X-Forwarded-For`.

Forwarding headers are deployment metadata rather than a new public API input,
so the login request body and OpenAPI schema remain unchanged. See
[ADR 0011](adr/0011-trusted-client-context.md) for the complete trust decision.

## Metrics and logs

Prometheus metrics:

- `payflow.auth.login.rate_limit.decisions`
  - `outcome=allowed|blocked`
  - `dimension=none|identity|client|both`
- `payflow.auth.login.rate_limit.redis.failures`
  - `operation=evaluate|reset`
- `payflow.security.client_context.decisions`
  - `source=direct_peer|forwarded|x_forwarded_for`
  - `outcome=direct|resolved|untrusted_peer|missing_header|malformed_header|oversized_header|excessive_hops`

The client-context metric has a fixed matrix of 21 source/outcome series.
The observer API receives only enum dimensions; it cannot receive a client
address or servlet request.

Security log events identify only the event type, blocked dimension, and request
path. They must not include raw identity values, passwords, tokens, client
addresses, address digests, or forwarding-header contents.

## Local verification

Start infrastructure and the application:

```powershell
docker compose up -d postgres redis kafka
.\mvnw.cmd spring-boot:run
```

Import and run the complete dedicated Postman collection:

```text
postman/PayFlow.login-rate-limit.postman_collection.json
```

Run it separately from the normal PayFlow workflow because it intentionally
consumes login attempts.

Automated verification:

```powershell
.\mvnw.cmd -Dtest=RedisLoginRateLimitAdapterIntegrationTest,LoginRateLimitHttpIntegrationTest,LoginRateLimitUnavailableHttpIntegrationTest,ServletClientAddressResolverTest,ClientAddressResolutionMetricsTest test
```

The full project gate remains:

```powershell
.\mvnw.cmd clean verify
```

## Operational checks

When investigating unexpected `429` responses:

1. confirm the configured identity and client thresholds
2. inspect the `Retry-After` value rather than retrying continuously
3. verify whether traffic is arriving through a shared proxy or NAT boundary
4. review only low-cardinality limiter metrics and safe security events
5. never inspect or export raw credentials for diagnosis

When Redis is unavailable, restore Redis connectivity and verify health before
retrying login traffic. Bypassing the limiter during an outage changes the
security contract and requires a separate, explicit operational decision.
