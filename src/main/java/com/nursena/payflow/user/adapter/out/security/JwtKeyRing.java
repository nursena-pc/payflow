package com.nursena.payflow.user.adapter.out.security;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

record JwtKeyRing(
    JwtSigningKey activeSigningKey,
    List<JwtVerificationKey> verificationKeys
) {

    JwtKeyRing {
        Objects.requireNonNull(
            activeSigningKey,
            "activeSigningKey must not be null"
        );
        Objects.requireNonNull(
            verificationKeys,
            "verificationKeys must not be null"
        );

        verificationKeys = List.copyOf(
            verificationKeys
        );

        if (verificationKeys.isEmpty()) {
            throw new IllegalArgumentException(
                "At least one JWT verification key is required"
            );
        }

        Set<JwtKeyId> keyIds = new HashSet<>();
        Set<BigInteger> moduli = new HashSet<>();

        for (JwtVerificationKey key : verificationKeys) {
            Objects.requireNonNull(
                key,
                "verification key must not be null"
            );

            if (!keyIds.add(key.keyId())) {
                throw new IllegalArgumentException(
                    "JWT verification key IDs must be unique"
                );
            }

            if (!moduli.add(key.publicKey().getModulus())) {
                throw new IllegalArgumentException(
                    "JWT verification keys must not alias "
                        + "the same RSA key material"
                );
            }
        }

        JwtVerificationKey activeVerificationKey =
            activeSigningKey.verificationKey();

        if (!verificationKeys.contains(activeVerificationKey)) {
            throw new IllegalArgumentException(
                "The active JWT key must be trusted for verification"
            );
        }
    }

    Set<String> verificationKeyIds() {
        return verificationKeys.stream()
            .map(key -> key.keyId().value())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public String toString() {
        return "JwtKeyRing[activeKeyId="
            + activeSigningKey.keyId().value()
            + ", verificationKeyIds="
            + verificationKeyIds()
            + "]";
    }
}
