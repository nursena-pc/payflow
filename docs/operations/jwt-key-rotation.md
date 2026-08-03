# JWT signing-key rotation

## Purpose

PayFlow v0.12.0 supports one active RSA signing key and one optional previous
verification key. Newly issued access tokens carry the active `kid`; the
resource server verifies only RS256 tokens selected by the configured active or
previous identifier.

This runbook covers local key generation, production configuration, planned
rotation, rollback, retirement, and emergency recovery. PayFlow remains a
simulated educational backend and does not provide a managed KMS.

## Operating modes

| Mode | Intended use | Restart continuity | Previous key |
|---|---|---:|---:|
| `ephemeral` | local development and tests | No | Not allowed |
| `configured` | production-profile and rotation verification | Yes | Optional |

The `production` Spring profile forces configured mode. Omitting a required
location therefore stops startup instead of falling back to an ephemeral key.

## Generate local rotation material

Create runtime material only in the ignored `.runtime/jwt` directory:

```bash
mkdir -p .runtime/jwt

openssl genpkey \
  -algorithm RSA \
  -pkeyopt rsa_keygen_bits:2048 \
  -out .runtime/jwt/active-private.pem

openssl pkey \
  -in .runtime/jwt/active-private.pem \
  -pubout \
  -out .runtime/jwt/active-public.pem

chmod 0400 .runtime/jwt/active-private.pem
chmod 0444 .runtime/jwt/active-public.pem
```

The private key must be PKCS#8 with `BEGIN PRIVATE KEY`. The public key must use
the X.509 SubjectPublicKeyInfo `BEGIN PUBLIC KEY` form. PKCS#1
`BEGIN RSA PRIVATE KEY` input is rejected.

Never add `.runtime`, PEM files, Base64 key material, or real key locations to
Git. Production keys must be generated and delivered by the deployment secret
system, not copied from this local example.

## Configure a production-profile process

The process requires these values:

```text
SPRING_PROFILES_ACTIVE=production
JWT_ACTIVE_KEY_ID=signing-2026-08
JWT_ACTIVE_PRIVATE_KEY_LOCATION=file:/run/secrets/payflow/jwt/active-private.pem
JWT_ACTIVE_PUBLIC_KEY_LOCATION=file:/run/secrets/payflow/jwt/active-public.pem
```

During an overlap window, also set:

```text
JWT_PREVIOUS_KEY_ID=signing-2026-07
JWT_PREVIOUS_PUBLIC_KEY_LOCATION=file:/run/secrets/payflow/jwt/previous-public.pem
```

Key locations use Spring resource syntax. Mount the containing secret directory
read-only and grant the application UID only the minimum read permission.
Private material must not be placed in command history, Compose YAML, the
repository, container images, logs, metrics, or support output.

## Startup validation

Startup fails before serving requests when:

- an active location is absent or unreadable
- only one previous-key field is present
- a key identifier is blank, duplicated, longer than 64 characters, or uses an
  unsupported character
- a PEM label, Base64 payload, RSA encoding, or resource size is invalid
- an RSA public key is weaker than 2,048 bits
- the active public and private keys do not match
- active and previous entries reuse the same RSA key material

The failure identifies the violated contract without printing PEM or private
key bytes.

## Planned rotation

Assume A is active and B is the next key.

### 1. Prepare both directions

Generate B and distribute its private key only to the signing workload. Prepare
two complete configurations before changing traffic:

| Configuration | Active signer | Previous verifier |
|---|---|---|
| Forward | B | A public key |
| Rollback | A | B public key |

The rollback configuration is required because instances using B may issue
tokens before a rollback decision.

### 2. Activate B

Restart instances with the forward configuration. Confirm that:

- a new login or refresh response carries B's `kid`
- a pre-activation A token still reaches an authenticated endpoint
- tokens with a missing or unknown `kid` receive the existing authentication
  failure contract
- logs and metrics contain neither PEM content nor bearer tokens

Do not remove A while any valid A token can still exist.

### 3. Observe the overlap

Keep A for at least the configured access-token TTL plus the deployment's
allowed clock-skew margin. PayFlow's default access-token TTL is 15 minutes.
Use authentication failure rate and deployment health signals; do not log raw
access tokens for diagnosis.

### 4. Retire A

After the overlap window, remove both previous-key variables and restart. A
tokens must then fail; B tokens remain valid.

## Rollback

If activation fails, restart with the prepared rollback configuration:

- active A private/public key
- previous B public key

Use a v0.12-compatible binary so that B's `kid` and verification-only overlap
remain understood. Keep B until every B token has expired, then remove it.
Rolling back to a pre-v0.12 binary does not preserve this guarantee.

## Emergency compromise response

If the active private key may be compromised:

1. stop issuing with the compromised key
2. generate and activate a replacement key with a new `kid`
3. do not retain the compromised public key as previous when immediate
   invalidation is required
4. restart all instances and verify the active `kid`
5. preserve security evidence without copying tokens or key material

This intentionally invalidates access tokens signed by the compromised key.
Refresh-session protections remain separate and can issue fresh access tokens
after normal authentication checks.

## Explicit limits

- no dynamic or scheduled key reload
- no remote KMS, HSM, Vault, or cloud key-provider adapter
- no public JWKS endpoint
- no more than one previous verification key
- no algorithms other than RS256
- no immediate per-token access-token revocation

See [ADR 0012](../adr/0012-jwt-signing-key-rotation.md) for the architectural
decision and [the delivery roadmap](../roadmap.md) for release gates.
