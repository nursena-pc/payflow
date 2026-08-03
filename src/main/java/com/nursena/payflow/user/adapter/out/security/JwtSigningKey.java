package com.nursena.payflow.user.adapter.out.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

record JwtSigningKey(
    JwtKeyId keyId,
    RSAPublicKey publicKey,
    RSAPrivateKey privateKey
) {

    JwtSigningKey {
        new JwtVerificationKey(
            keyId,
            publicKey
        );

        Objects.requireNonNull(
            privateKey,
            "privateKey must not be null"
        );

        if (
            !publicKey.getModulus().equals(
                privateKey.getModulus()
            )
        ) {
            throw new IllegalArgumentException(
                "JWT active public and private keys do not match"
            );
        }
    }

    JwtVerificationKey verificationKey() {
        return new JwtVerificationKey(
            keyId,
            publicKey
        );
    }

    @Override
    public String toString() {
        return "JwtSigningKey[keyId="
            + keyId.value()
            + ", publicKey=[REDACTED], privateKey=[REDACTED]]";
    }
}
