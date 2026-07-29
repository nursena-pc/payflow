package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RefreshSessionLifetimePolicyTest {

    private static final Instant ISSUED_AT =
        Instant.parse(
            "2026-07-28T12:00:00Z"
        );

    @Test
    void shouldCalculateAbsoluteFamilyExpiration() {
        RefreshSessionLifetimePolicy policy =
            new RefreshSessionLifetimePolicy(
                Duration.ofDays(7),
                Duration.ofDays(30)
            );

        assertThat(
            policy.familyExpiresAt(
                ISSUED_AT
            )
        )
            .isEqualTo(
                ISSUED_AT.plus(
                    Duration.ofDays(30)
                )
            );
    }

    @Test
    void shouldCalculateRefreshTokenExpiration() {
        RefreshSessionLifetimePolicy policy =
            new RefreshSessionLifetimePolicy(
                Duration.ofDays(7),
                Duration.ofDays(30)
            );

        Instant familyExpiresAt =
            ISSUED_AT.plus(
                Duration.ofDays(30)
            );

        assertThat(
            policy.refreshTokenExpiresAt(
                ISSUED_AT,
                familyExpiresAt
            )
        )
            .isEqualTo(
                ISSUED_AT.plus(
                    Duration.ofDays(7)
                )
            );
    }

    @Test
    void shouldCapRefreshTokenAtFamilyExpiration() {
        RefreshSessionLifetimePolicy policy =
            new RefreshSessionLifetimePolicy(
                Duration.ofDays(60),
                Duration.ofDays(30)
            );

        Instant familyExpiresAt =
            ISSUED_AT.plus(
                Duration.ofDays(30)
            );

        assertThat(
            policy.refreshTokenExpiresAt(
                ISSUED_AT,
                familyExpiresAt
            )
        )
            .isEqualTo(
                familyExpiresAt
            );
    }

    @ParameterizedTest
    @MethodSource("invalidFamilyExpirations")
    void shouldRequireFamilyExpirationAfterIssuance(
        Instant invalidFamilyExpiresAt
    ) {
        RefreshSessionLifetimePolicy policy =
            new RefreshSessionLifetimePolicy(
                Duration.ofDays(7),
                Duration.ofDays(30)
            );

        assertThatThrownBy(() ->
            policy.refreshTokenExpiresAt(
                ISSUED_AT,
                invalidFamilyExpiresAt
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "familyExpiresAt must be after issuedAt"
            );
    }

    @Test
    void shouldRequireIssuanceTime() {
        RefreshSessionLifetimePolicy policy =
            new RefreshSessionLifetimePolicy(
                Duration.ofDays(7),
                Duration.ofDays(30)
            );

        assertThatThrownBy(() ->
            policy.familyExpiresAt(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "issuedAt must not be null"
            );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositiveRefreshTokenTtl(
        Duration invalidDuration
    ) {
        assertThatThrownBy(() ->
            new RefreshSessionLifetimePolicy(
                invalidDuration,
                Duration.ofDays(30)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "refreshTokenTtl must be positive"
            );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositiveFamilyTtl(
        Duration invalidDuration
    ) {
        assertThatThrownBy(() ->
            new RefreshSessionLifetimePolicy(
                Duration.ofDays(7),
                invalidDuration
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "familyTtl must be positive"
            );
    }

    private static Stream<Instant>
    invalidFamilyExpirations() {
        return Stream.of(
            ISSUED_AT,
            ISSUED_AT.minusSeconds(1)
        );
    }

    private static Stream<Duration>
    nonPositiveDurations() {
        return Stream.of(
            Duration.ZERO,
            Duration.ofSeconds(-1)
        );
    }
}
