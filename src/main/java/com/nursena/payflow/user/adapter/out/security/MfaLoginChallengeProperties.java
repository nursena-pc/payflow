package com.nursena.payflow.user.adapter.out.security;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.security.mfa.login-challenge")
public record MfaLoginChallengeProperties(
    Duration ttl,
    int maxAttempts
) {
    public MfaLoginChallengeProperties {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (
            ttl.isZero()
                || ttl.isNegative()
                || ttl.compareTo(Duration.ofMinutes(15)) > 0
        ) {
            throw new IllegalArgumentException(
                "ttl must be positive and at most fifteen minutes"
            );
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException(
                "maxAttempts must be between 1 and 10"
            );
        }
    }
}
