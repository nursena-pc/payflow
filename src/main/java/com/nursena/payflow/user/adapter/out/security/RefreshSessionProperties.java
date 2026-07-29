package com.nursena.payflow.user.adapter.out.security;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.security.refresh-session"
)
public record RefreshSessionProperties(
    Duration refreshTokenTtl,
    Duration familyTtl
) {

    public RefreshSessionProperties {
        Objects.requireNonNull(
            refreshTokenTtl,
            "refreshTokenTtl must not be null"
        );
        Objects.requireNonNull(
            familyTtl,
            "familyTtl must not be null"
        );

        requirePositive(
            refreshTokenTtl,
            "refreshTokenTtl"
        );

        requirePositive(
            familyTtl,
            "familyTtl"
        );
    }

    private static void requirePositive(
        Duration value,
        String propertyName
    ) {
        if (
            value.isZero()
                || value.isNegative()
        ) {
            throw new IllegalArgumentException(
                propertyName + " must be positive"
            );
        }
    }
}
