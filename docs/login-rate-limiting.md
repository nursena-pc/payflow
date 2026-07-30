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
| Client | 20 attempts per 15 minutes | Direct servlet peer address |

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
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |

Threshold and window changes should be reviewed as security-policy changes.
Avoid values that create an easy denial-of-service path for shared networks.

## Client-address trust boundary

The controller currently uses the direct servlet peer address and does not
parse arbitrary `X-Forwarded-For` values. This avoids trusting attacker-supplied
forwarding headers.

When PayFlow is deployed behind a reverse proxy, the platform must establish a
trusted forwarding boundary before client-based thresholds are treated as
end-user IP limits. Without that boundary, requests may be grouped by the proxy
address. Do not enable unrestricted forwarded-header trust.

## Metrics and logs

Prometheus metrics:

- `payflow.auth.login.rate_limit.decisions`
  - `outcome=allowed|blocked`
  - `dimension=none|identity|client|both`
- `payflow.auth.login.rate_limit.redis.failures`
  - `operation=evaluate|reset`

Security log events identify only the event type, blocked dimension, and request
path. They must not include raw identity values, passwords, tokens, or client
addresses.

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
.\mvnw.cmd -Dtest=RedisLoginRateLimitAdapterIntegrationTest,LoginRateLimitHttpIntegrationTest,LoginRateLimitUnavailableHttpIntegrationTest test
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
