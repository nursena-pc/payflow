package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialGenerationPort;
import com.nursena.payflow.user.application.port.out
    .GeneratedAccountActionCredential;

final class SecureRandomAccountActionCredentialGenerationAdapter
    implements AccountActionCredentialGenerationPort {

    static final int ENTROPY_LENGTH_BYTES = 32;
    static final int ENCODED_CREDENTIAL_LENGTH = 43;

    private static final Base64.Encoder CREDENTIAL_ENCODER =
        Base64.getUrlEncoder()
            .withoutPadding();

    private final SecureRandom secureRandom;

    SecureRandomAccountActionCredentialGenerationAdapter(
        SecureRandom secureRandom
    ) {
        this.secureRandom = Objects.requireNonNull(
            secureRandom,
            "secureRandom must not be null"
        );
    }

    @Override
    public GeneratedAccountActionCredential generate() {
        byte[] randomBytes =
            new byte[ENTROPY_LENGTH_BYTES];

        try {
            secureRandom.nextBytes(randomBytes);

            String encodedCredential =
                CREDENTIAL_ENCODER.encodeToString(
                    randomBytes
                );

            if (
                encodedCredential.length()
                    != ENCODED_CREDENTIAL_LENGTH
            ) {
                throw new IllegalStateException(
                    "Generated account action credential has "
                        + "an unexpected encoded length."
                );
            }

            return new GeneratedAccountActionCredential(
                encodedCredential
            );
        } finally {
            Arrays.fill(
                randomBytes,
                (byte) 0
            );
        }
    }
}
