package com.nursena.payflow.user.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;

public final class AccountActionCredentialLifetimePolicy {

    private final Duration emailVerificationTtl;
    private final Duration passwordRecoveryTtl;

    public AccountActionCredentialLifetimePolicy(
        Duration emailVerificationTtl,
        Duration passwordRecoveryTtl
    ) {
        this.emailVerificationTtl = requirePositive(
            emailVerificationTtl,
            "emailVerificationTtl"
        );
        this.passwordRecoveryTtl = requirePositive(
            passwordRecoveryTtl,
            "passwordRecoveryTtl"
        );
    }

    public Instant expiresAt(
        AccountActionCredentialPurpose purpose,
        Instant issuedAt
    ) {
        AccountActionCredentialPurpose checkedPurpose =
            Objects.requireNonNull(
                purpose,
                "purpose must not be null"
            );
        Instant checkedIssuedAt = Objects.requireNonNull(
            issuedAt,
            "issuedAt must not be null"
        );

        Duration ttl = switch (checkedPurpose) {
            case EMAIL_VERIFICATION ->
                emailVerificationTtl;
            case PASSWORD_RECOVERY ->
                passwordRecoveryTtl;
        };

        return checkedIssuedAt.plus(ttl);
    }

    private static Duration requirePositive(
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

        return checkedValue;
    }
}
