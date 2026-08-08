package com.nursena.payflow.user.adapter.out.security;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.security.mfa.totp")
public record TotpProperties(
    String issuer,
    Duration enrollmentTtl
) {
    public TotpProperties {
        issuer = Objects.requireNonNull(
            issuer,
            "issuer must not be null"
        ).trim();
        enrollmentTtl = Objects.requireNonNull(
            enrollmentTtl,
            "enrollmentTtl must not be null"
        );

        if (issuer.isBlank() || issuer.length() > 64) {
            throw new IllegalArgumentException(
                "issuer must contain between 1 and 64 characters"
            );
        }

        if (
            enrollmentTtl.isZero()
                || enrollmentTtl.isNegative()
                || enrollmentTtl.compareTo(Duration.ofHours(1)) > 0
        ) {
            throw new IllegalArgumentException(
                "enrollmentTtl must be positive and at most one hour"
            );
        }
    }
}
