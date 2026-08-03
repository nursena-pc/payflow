package com.nursena.payflow.user.adapter.out.security;

import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

record JwtVerificationKey(
    JwtKeyId keyId,
    RSAPublicKey publicKey
) {

    private static final int MINIMUM_RSA_KEY_SIZE = 2048;

    JwtVerificationKey {
        Objects.requireNonNull(
            keyId,
            "keyId must not be null"
        );
        Objects.requireNonNull(
            publicKey,
            "publicKey must not be null"
        );

        if (
            publicKey.getModulus().bitLength()
                < MINIMUM_RSA_KEY_SIZE
        ) {
            throw new IllegalArgumentException(
                "JWT RSA keys must be at least 2048 bits"
            );
        }
    }

    @Override
    public String toString() {
        return "JwtVerificationKey[keyId="
            + keyId.value()
            + ", publicKey=[REDACTED]]";
    }
}
