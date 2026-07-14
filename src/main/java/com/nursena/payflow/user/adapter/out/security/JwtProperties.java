package com.nursena.payflow.user.adapter.out.security;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.security.jwt")
public record JwtProperties(
    String issuer,
    Duration accessTokenTtl
) {

    public JwtProperties {
        Objects.requireNonNull(
            issuer,
            "issuer must not be null"
        );
        Objects.requireNonNull(
            accessTokenTtl,
            "accessTokenTtl must not be null"
        );

        if (issuer.isBlank()) {
            throw new IllegalArgumentException(
                "issuer must not be blank"
            );
        }

        if (accessTokenTtl.isZero()
            || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                "accessTokenTtl must be positive"
            );
        }
    }
}
