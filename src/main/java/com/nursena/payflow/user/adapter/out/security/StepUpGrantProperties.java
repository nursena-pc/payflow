package com.nursena.payflow.user.adapter.out.security;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.security.mfa.step-up")
public record StepUpGrantProperties(Duration ttl) {
    public StepUpGrantProperties {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()
            || ttl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException(
                "ttl must be positive and at most fifteen minutes"
            );
        }
    }
}
