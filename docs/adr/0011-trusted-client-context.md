# ADR 0011: Resolve effective client addresses only through trusted proxies

- Status: Accepted
- Date: 2026-07-31

## Context

PayFlow applies a Redis-backed client counter to
`POST /api/v1/auth/login`. The original implementation used the direct servlet
peer. That is safe against header spoofing, but deployments behind a reverse
proxy group requests by the proxy address rather than by the effective client.

Blindly trusting `Forwarded`, `X-Forwarded-For`, or framework-wide forwarded
header processing would allow an untrusted caller to choose the identity used
by a security control. The decision must therefore begin with the network peer
that actually connected to PayFlow.

The solution must support IPv4 and IPv6, avoid DNS resolution, bound parser
work, preserve existing `401`, `429`, and fail-closed `503` contracts, and keep
raw addresses out of Redis keys, metric labels, and logs.

## Decision

PayFlow will resolve an effective client address only when the direct servlet
peer belongs to an explicitly configured trusted-proxy CIDR.

The secure default is an empty trusted-proxy list. In that state, forwarding
headers never influence the result.

Configuration:

```yaml
payflow:
  security:
    client-context:
      trusted-proxy-cidrs: ${TRUSTED_PROXY_CIDRS:}
      max-forwarded-header-length: ${FORWARDED_HEADER_MAX_LENGTH:4096}
      max-forwarded-hops: ${FORWARDED_MAX_HOPS:16}
```

Trusted networks are validated at startup. The configuration rejects:

- non-literal addresses
- invalid prefix lengths
- CIDRs with host bits set
- duplicate networks
- IPv4 or IPv6 `/0` trust-all networks
- more than 64 trusted networks
- header limits outside `256..16384`
- hop limits outside `1..64`

## Header precedence

For a trusted direct peer:

1. select `Forwarded` when present
2. otherwise select `X-Forwarded-For`
3. do not downgrade to `X-Forwarded-For` when a present `Forwarded` value fails

This prevents an attacker or misconfigured intermediary from bypassing a bad
preferred header by supplying a second, more permissive representation.

`X-Real-IP` and other proprietary headers are not supported.

## Chain resolution

The selected header is parsed into an ordered list of literal IP addresses.

PayFlow walks the chain from right to left:

1. configured trusted proxy hops are skipped
2. the first untrusted address becomes the effective client
3. if every supplied hop is trusted, the leftmost address is selected

The algorithm supports normalized IPv4 and IPv6 values, including bracketed
IPv6 node identifiers where the `Forwarded` grammar requires them. Hostnames are
never resolved.

## Failure behavior

The direct peer is used when:

- the direct peer is not trusted
- no supported forwarding header is present
- the selected header is malformed
- a node is `unknown` or obfuscated
- quoting, brackets, or ports are invalid
- the combined selected header exceeds the configured length
- the chain exceeds the configured hop limit

A malformed preferred `Forwarded` value does not trigger fallback to
`X-Forwarded-For`.

The resolver does not change the public login error model. Authentication still
returns generic invalid credentials below threshold, stable `429` with positive
`Retry-After` when blocked, and fail-closed `503` when Redis cannot make a safe
decision.

## Application boundaries

`ClientAddressResolver` is an inbound web-boundary abstraction. The servlet
implementation owns:

- direct-peer extraction
- header selection and parsing
- trusted-chain evaluation
- safe fallback behavior
- resolution decision observation

The user login controller receives only the normalized effective address and
passes it into the existing authentication command. The application rate-limit
port remains independent from servlet and forwarding-header types.

## Sensitive data and observability

Redis key material continues to use SHA-256 digests rather than raw addresses.

Client-context decisions are published through:

```text
payflow.security.client_context.decisions
```

Only bounded enum tags are allowed:

```text
source=direct_peer|forwarded|x_forwarded_for
outcome=direct|resolved|untrusted_peer|missing_header|malformed_header|oversized_header|excessive_hops
```

The observer API does not accept an address, request, or header value. No raw
client address or forwarding value is added to logs.

## Rejected alternatives

### Trust every forwarding header

Rejected because any direct caller could choose the client identity used by the
login limiter.

### Enable framework-wide forwarded-header transformation

Rejected for this increment because it changes request semantics globally and
makes the trust decision less explicit at the security-control boundary.

### Resolve proxy hostnames

Rejected because DNS changes would mutate the trust boundary at runtime and
literal-only parsing avoids blocking name resolution during request handling.

### Accept trust-all CIDRs

Rejected because `0.0.0.0/0` or `::/0` removes the distinction between trusted
infrastructure and untrusted callers.

### Use raw addresses as metric labels

Rejected because address labels create unbounded cardinality and expose
sensitive network identifiers.

## Consequences

Benefits:

- untrusted peers cannot spoof the client identity through headers
- known reverse proxies can preserve effective client-based login limits
- IPv4 and IPv6 chains resolve deterministically
- malformed input fails safely
- parser work and metric cardinality are bounded
- existing public authentication contracts remain stable

Costs:

- operators must configure the exact network peer CIDRs
- incorrect trusted CIDRs may group traffic by the proxy or trust the wrong hop
- all-trusted chains select the leftmost supplied address
- only `Forwarded` and `X-Forwarded-For` are supported
- proxy topology changes require configuration review
