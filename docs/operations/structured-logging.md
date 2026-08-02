# Structured logging operations guide

PayFlow emits human-readable console logs by default and single-line JSON logs when either the `structured-logging` or `production` Spring profile is active.

## Activation

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "structured-logging"
mvn spring-boot:run
```

Docker or another process manager:

```text
SPRING_PROFILES_ACTIVE=production
```

The JSON encoder writes one object per UNIX-delimited line. This makes the output safe for container log collectors and line-oriented ingestion pipelines.

## Stable fields

Every structured log can contain the common fields below:

| Field | Meaning |
|---|---|
| `timestamp` | Encoder-generated event timestamp |
| `service` | Spring application name |
| `schemaVersion` | Structured-log contract version |
| `level` | Log severity |
| `logger` | Logger name |
| `thread` | Request-processing thread |
| `message` | Human-readable event message |
| `correlationId` | Effective trusted request correlation identifier |
| `exception` | Bounded exception representation when explicitly logged |

A completed HTTP request emits exactly one `http.request.completed` event with these bounded fields:

| Field | Meaning |
|---|---|
| `event` | Always `http.request.completed` |
| `http.method` | Normalized HTTP method or `UNKNOWN` |
| `http.route` | Spring MVC route template or `UNMATCHED` |
| `http.status_code` | Effective response status |
| `duration_ms` | Non-negative elapsed request time |
| `outcome` | `SUCCESS`, `CLIENT_ERROR`, or `SERVER_ERROR` |

`http.route` uses Spring MVC's best matching route pattern after request dispatch. It never falls back to the raw request URI. Requests without a resolved route use the fixed value `UNMATCHED`.

## Security boundaries

The completion event is deliberately smaller than the HTTP request.

- Query strings are never logged.
- Request and response bodies are never logged.
- Authorization and cookie headers are never logged.
- Raw URI paths and path-variable values are never logged.
- User identifiers, email addresses, wallet identifiers, transfer identifiers, amounts, balances, and idempotency keys are not completion-event fields.
- Unrecognized or unsafe methods use `UNKNOWN`.
- Missing or unsafe route patterns use `UNMATCHED`.
- Completion events do not include the handled application exception and therefore do not duplicate exception stack traces.

The JSON encoder also masks known credential fields and bearer/JWT values with `[REDACTED]`.

## Example

```json
{
  "timestamp": "2026-08-02T01:30:00.000Z",
  "service": "payflow",
  "schemaVersion": 1,
  "level": "INFO",
  "logger": "com.nursena.payflow.observability.logging.Slf4jRequestCompletionLogger",
  "thread": "http-nio-8080-exec-1",
  "message": "HTTP request completed: method=POST, route=/api/v1/transfers, status=201, durationMs=24, outcome=SUCCESS.",
  "correlationId": "request-123",
  "event": "http.request.completed",
  "http.method": "POST",
  "http.route": "/api/v1/transfers",
  "http.status_code": "201",
  "duration_ms": "24",
  "outcome": "SUCCESS"
}
```

## Outcome rules

- Statuses below `400` are `SUCCESS`.
- Statuses from `400` through `499` are `CLIENT_ERROR`.
- Statuses from `500` through `599` are `SERVER_ERROR`.
- An exception that escapes the request chain is always `SERVER_ERROR`; its effective status is logged as `500` when the response has not already selected a server-error status.

## Correlation API contract

Every API response advertises `X-Correlation-ID` through the generated OpenAPI document. The header is required in the response contract, has a maximum length of 64 characters, and follows the same allowlisted character policy enforced at runtime.

The standard Postman collection verifies the header after every request, checks the 64-character upper bound, and stores the effective value in the active environment as `correlationId`.

## Synchronous and asynchronous boundaries

The servlet MDC contract is intentionally synchronous. It starts when `RequestCorrelationFilter` accepts or generates the effective identifier and ends in that filter's `finally` block.

Thread pools, scheduled jobs, transactional outbox publication, Kafka consumers, retries, and dead-letter processing do not inherit servlet MDC implicitly. PayFlow does not treat a request correlation ID as a business identifier, authentication credential, transaction identifier, idempotency key, or Kafka partition key.

A future asynchronous propagation design must be an explicit event-schema decision with bounded validation, compatibility rules, consumer cleanup, and dedicated tests. Until that work is delivered, asynchronous processing uses its existing bounded event, transaction, command, and audit identifiers.

## Exception and stack-trace policy

Stack traces are emitted only when application code explicitly logs a throwable. The request-completion event never includes a throwable and therefore does not duplicate centralized exception logging.

Both structured production logs and local plain logs use the same application logging calls. In structured profiles, exception output is bounded by the configured shortened throwable converter:

- maximum depth per throwable: 30
- maximum encoded exception length: 8192 characters
- root cause first
- shortened class names

No profile enables request bodies, response bodies, query strings, credentials, cookies, authorization headers, raw financial payloads, or unbounded exception data.

## Verification
Run the focused observability contracts:

```powershell
mvn -B -ntp -Dtest=HttpRequestOutcomeTest,Slf4jRequestCompletionLoggerTest,RequestCompletionHttpIntegrationTest,RequestCorrelationFilterTest,RequestCorrelationHttpIntegrationTest,StructuredLoggingConfigurationContractTest,StructuredJsonEncodingTest test
```

Run the complete suite before merging:

```powershell
mvn -B -ntp clean test
```