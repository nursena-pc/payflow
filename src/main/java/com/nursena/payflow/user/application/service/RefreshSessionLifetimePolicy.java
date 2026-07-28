package com.nursena.payflow.user.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class RefreshSessionLifetimePolicy {

    private final Duration refreshTokenTtl;
    private final Duration familyTtl;

    public RefreshSessionLifetimePolicy(
        Duration refreshTokenTtl,
        Duration familyTtl
    ) {
        this.refreshTokenTtl =
            requirePositive(
                refreshTokenTtl,
                "refreshTokenTtl"
            );

        this.familyTtl =
            requirePositive(
                familyTtl,
                "familyTtl"
            );
    }

    public Instant familyExpiresAt(
        Instant issuedAt
    ) {
        Instant checkedIssuedAt =
            Objects.requireNonNull(
                issuedAt,
                "issuedAt must not be null"
            );

        return checkedIssuedAt.plus(
            familyTtl
        );
    }

    public Instant refreshTokenExpiresAt(
        Instant issuedAt,
        Instant familyExpiresAt
    ) {
        Instant checkedIssuedAt =
            Objects.requireNonNull(
                issuedAt,
                "issuedAt must not be null"
            );

        Instant checkedFamilyExpiresAt =
            Objects.requireNonNull(
                familyExpiresAt,
                "familyExpiresAt must not be null"
            );

        if (
            !checkedFamilyExpiresAt.isAfter(
                checkedIssuedAt
            )
        ) {
            throw new IllegalArgumentException(
                "familyExpiresAt must be after issuedAt"
            );
        }

        Instant requestedExpiresAt =
            checkedIssuedAt.plus(
                refreshTokenTtl
            );

        return requestedExpiresAt.isAfter(
            checkedFamilyExpiresAt
        )
            ? checkedFamilyExpiresAt
            : requestedExpiresAt;
    }

    private static Duration requirePositive(
        Duration value,
        String propertyName
    ) {
        Duration checkedValue =
            Objects.requireNonNull(
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
