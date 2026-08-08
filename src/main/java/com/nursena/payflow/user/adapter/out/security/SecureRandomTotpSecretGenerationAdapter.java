package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;
import java.util.Objects;

import com.nursena.payflow.user.application.port.out.GeneratedTotpSecret;
import com.nursena.payflow.user.application.port.out.TotpSecretGenerationPort;

final class SecureRandomTotpSecretGenerationAdapter
    implements TotpSecretGenerationPort {

    private static final int SECRET_LENGTH = 20;

    private final SecureRandom secureRandom;

    SecureRandomTotpSecretGenerationAdapter(
        SecureRandom secureRandom
    ) {
        this.secureRandom = Objects.requireNonNull(
            secureRandom,
            "secureRandom must not be null"
        );
    }

    @Override
    public GeneratedTotpSecret generate() {
        byte[] secret = new byte[SECRET_LENGTH];
        secureRandom.nextBytes(secret);

        return new GeneratedTotpSecret(
            secret,
            Base32Codec.encode(secret)
        );
    }
}
