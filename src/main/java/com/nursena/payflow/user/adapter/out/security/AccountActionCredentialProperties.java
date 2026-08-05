package com.nursena.payflow.user.adapter.out.security;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties
    .ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.security.account-action"
)
public record AccountActionCredentialProperties(
    Duration emailVerificationTtl,
    Duration passwordRecoveryTtl
) {

    public AccountActionCredentialProperties {
        requirePositive(
            emailVerificationTtl,
            "emailVerificationTtl"
        );
        requirePositive(
            passwordRecoveryTtl,
            "passwordRecoveryTtl"
        );
    }

    private static void requirePositive(
        Duration value,
        String propertyName
    ) {
        Duration checkedValue = Objects.requireNonNull(
            value,
            propertyName + " must not be null"
        );

        if (
            checkedValue.isZero()
                || checkedValue.isNegative()
        ) {
            throw new IllegalArgumentException(
                propertyName + " must be positive"
            );
        }
    }
}
