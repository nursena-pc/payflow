package com.nursena.payflow.user.adapter.out.security;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.security.jwt.key-set"
)
public record JwtKeySetProperties(
    JwtKeyProviderMode providerMode,
    String activeKeyId,
    String activePrivateKeyLocation,
    String activePublicKeyLocation,
    String previousKeyId,
    String previousPublicKeyLocation
) {

    public JwtKeySetProperties {
        Objects.requireNonNull(
            providerMode,
            "providerMode must not be null"
        );

        activeKeyId = JwtKeyId.of(
            activeKeyId
        ).value();

        activePrivateKeyLocation = normalizeOptional(
            activePrivateKeyLocation
        );
        activePublicKeyLocation = normalizeOptional(
            activePublicKeyLocation
        );
        previousKeyId = normalizeBlankKeyId(
            previousKeyId
        );
        previousPublicKeyLocation = normalizeOptional(
            previousPublicKeyLocation
        );

        boolean previousIdConfigured =
            previousKeyId != null;

        boolean previousLocationConfigured =
            previousPublicKeyLocation != null;

        if (
            previousIdConfigured
                != previousLocationConfigured
        ) {
            throw new IllegalArgumentException(
                "Previous JWT key ID and public-key location "
                    + "must be configured together"
            );
        }

        if (previousIdConfigured) {
            previousKeyId = JwtKeyId.of(
                previousKeyId
            ).value();

            if (previousKeyId.equals(activeKeyId)) {
                throw new IllegalArgumentException(
                    "Active and previous JWT key IDs must differ"
                );
            }
        }

        if (
            providerMode
                == JwtKeyProviderMode.CONFIGURED
        ) {
            requireLocation(
                activePrivateKeyLocation,
                "Active JWT private-key location is required"
            );
            requireLocation(
                activePublicKeyLocation,
                "Active JWT public-key location is required"
            );
        } else if (
            activePrivateKeyLocation != null
                || activePublicKeyLocation != null
                || previousIdConfigured
        ) {
            throw new IllegalArgumentException(
                "Ephemeral JWT mode must not declare key locations "
                    + "or a previous key"
            );
        }
    }

    boolean hasPreviousKey() {
        return previousKeyId != null;
    }

    private static String normalizeOptional(
        String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String normalizeBlankKeyId(
        String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value;
    }

    private static void requireLocation(
        String value,
        String message
    ) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
