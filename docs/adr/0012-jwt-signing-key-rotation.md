# ADR 0012: Rotate JWT signing keys through a bounded key-provider contract

- Status: Accepted
- Date: 2026-08-03

## Context

PayFlow issues short-lived RSA-signed access tokens. Before v0.12.0, one RSA
key pair was generated when the application started. Restarting the process
therefore invalidated every access token issued by the previous process, and
there was no explicit overlap window for a planned or emergency rotation.

Key rotation must not weaken token verification, leak private material, place
Nimbus or Spring types in application or domain code, or silently accept an
attacker-selected algorithm. A deployment also needs a bounded rollback path:
tokens issued immediately before and after activation must remain verifiable
for no longer than the documented overlap window.

## Decision

PayFlow introduces an adapter-local `JwtKeyProvider` contract that returns one
immutable key-ring snapshot at startup:

- exactly one active RSA signing key with private and public material
- the active public key as a verification key
- at most one previous, verification-only public key
- one unique, bounded `kid` for each key

The application and domain layers continue to depend only on
`AccessTokenGenerationPort`. They do not know about PEM, files, Nimbus, Spring,
KMS products, or rotation mechanics.

New tokens are signed only with RS256. The encoder places the active stable
`kid` in every token header. Verification is also pinned to RS256 and refuses
tokens whose `kid` is missing or does not select the configured active or
previous key.

## Key-provider modes

### Ephemeral

`EPHEMERAL` generates one 2,048-bit RSA key at startup. It is the default only
for non-production local development and tests. It has no restart or
multi-instance continuity guarantee and cannot declare a previous key.

### Configured

`CONFIGURED` loads:

- one PKCS#8 `PRIVATE KEY` PEM resource for active signing
- one X.509 `PUBLIC KEY` PEM resource for active verification
- optionally one X.509 `PUBLIC KEY` PEM resource for previous verification

The `production` profile always selects configured mode. Startup fails when a
resource is missing, unreadable, larger than 16 KiB, malformed, not RSA, weaker
than 2,048 bits, or when the active public and private keys do not form one key
pair.

Key identifiers accept only 1 to 64 ASCII letters, digits, underscores, or
hyphens. Active and previous identifiers must differ. The same RSA modulus
cannot be registered under two identifiers.

## Rotation protocol

For current key A and next key B:

1. Generate B outside the repository and prepare a forward configuration with
   `active=B` and `previous=A`.
2. Prepare a rollback configuration with `active=A` and `previous=B` before
   activation.
3. Deploy the forward configuration. New tokens use B; unexpired A tokens
   continue to verify.
4. Observe authentication failures for at least the access-token TTL plus the
   allowed deployment clock-skew margin.
5. Remove A only after the overlap window has elapsed.

Rollback uses the prepared inverse configuration and a v0.12-compatible
binary. Dynamic reload is deliberately not supported; every key-ring change
requires a controlled restart.

## Security boundaries

- Private key bytes are never returned from a bean, endpoint, log, metric, or
  exception message.
- Key locations and key identifiers are configuration metadata, not token
  trust decisions by themselves.
- `kid` selects a key only inside the already configured bounded key ring.
- Unknown or absent `kid` values fail authentication; the verifier does not
  try every key.
- Algorithm negotiation is not supported. Both issuance and verification use
  RS256.
- The previous key is verification-only and cannot issue new tokens.

## Consequences

### Positive

- planned rotation does not force immediate logout of all access-token holders
- rollback can preserve tokens issued on either side of activation
- production startup proves key presence, strength, and active-pair integrity
- a future KMS or HSM adapter can implement the provider boundary without
  changing application or domain code

### Costs

- operators must manage key files, identifiers, permissions, and overlap timing
- a restart is required for each rotation phase
- deployment automation must mount private material read-only and make it
  readable by the application process

## Rejected alternatives

### Verify every configured key when `kid` is absent

Rejected because it weakens the key-selection contract and permits legacy or
malformed tokens to bypass identifier validation.

### Accept multiple algorithms

Rejected because PayFlow has one RSA contract. Algorithm agility would expand
the verification surface without a current product requirement.

### Put KMS types in the application port

Rejected because KMS access, PEM parsing, and Nimbus key selection are adapter
concerns. The business use case only needs a generated access token.

### Hot-reload key material

Rejected for v0.12.0 because refresh concurrency, partial failure, cache
coherence, and rollback require a separate threat model and operational design.
