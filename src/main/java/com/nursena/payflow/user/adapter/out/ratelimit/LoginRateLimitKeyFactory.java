package com.nursena.payflow.user.adapter.out.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import com.nursena.payflow.user.domain.model.EmailAddress;

final class LoginRateLimitKeyFactory {

    private static final String IDENTITY_PREFIX =
        "payflow:security:login:identity:";

    private static final String CLIENT_PREFIX =
        "payflow:security:login:client:";

    private LoginRateLimitKeyFactory() {
    }

    static String identityKey(
        EmailAddress identity
    ) {
        Objects.requireNonNull(
            identity,
            "identity must not be null"
        );

        return IDENTITY_PREFIX
            + sha256(identity.value());
    }

    static String clientKey(
        String clientAddress
    ) {
        Objects.requireNonNull(
            clientAddress,
            "clientAddress must not be null"
        );

        String normalizedAddress =
            clientAddress
                .trim()
                .toLowerCase(Locale.ROOT);

        if (normalizedAddress.isBlank()) {
            throw new IllegalArgumentException(
                "clientAddress must not be blank"
            );
        }

        return CLIENT_PREFIX
            + sha256(normalizedAddress);
    }

    private static String sha256(
        String value
    ) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            return HexFormat.of()
                .formatHex(
                    digest.digest(
                        value.getBytes(
                            StandardCharsets.UTF_8
                        )
                    )
                );
        } catch (
            NoSuchAlgorithmException exception
        ) {
            throw new IllegalStateException(
                "SHA-256 is not available",
                exception
            );
        }
    }
}
