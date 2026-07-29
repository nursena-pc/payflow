package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import com.nursena.payflow.user.application.port.out.GeneratedRefreshToken;
import com.nursena.payflow.user.application.port.out.RefreshTokenGenerationPort;

final class SecureRandomRefreshTokenGenerationAdapter
    implements RefreshTokenGenerationPort {

    static final int ENTROPY_LENGTH_BYTES = 32;
    static final int ENCODED_TOKEN_LENGTH = 43;

    private static final Base64.Encoder TOKEN_ENCODER =
        Base64.getUrlEncoder()
            .withoutPadding();

    private final SecureRandom secureRandom;

    SecureRandomRefreshTokenGenerationAdapter(
        SecureRandom secureRandom
    ) {
        this.secureRandom =
            Objects.requireNonNull(
                secureRandom,
                "secureRandom must not be null"
            );
    }

    @Override
    public GeneratedRefreshToken generate() {
        byte[] randomBytes =
            new byte[ENTROPY_LENGTH_BYTES];

        try {
            secureRandom.nextBytes(randomBytes);

            String encodedToken =
                TOKEN_ENCODER.encodeToString(
                    randomBytes
                );

            if (
                encodedToken.length()
                    != ENCODED_TOKEN_LENGTH
            ) {
                throw new IllegalStateException(
                    "Generated refresh token has "
                        + "an unexpected encoded length."
                );
            }

            return new GeneratedRefreshToken(
                encodedToken
            );
        } finally {
            Arrays.fill(
                randomBytes,
                (byte) 0
            );
        }
    }
}
