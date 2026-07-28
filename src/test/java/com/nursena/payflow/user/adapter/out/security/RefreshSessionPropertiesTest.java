package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RefreshSessionPropertiesTest {

    @Test
    void shouldRetainValidDurations() {
        Duration refreshTokenTtl =
            Duration.ofDays(7);

        Duration familyTtl =
            Duration.ofDays(30);

        RefreshSessionProperties properties =
            new RefreshSessionProperties(
                refreshTokenTtl,
                familyTtl
            );

        assertThat(properties.refreshTokenTtl())
            .isEqualTo(refreshTokenTtl);

        assertThat(properties.familyTtl())
            .isEqualTo(familyTtl);
    }

    @Test
    void shouldRequireRefreshTokenTtl() {
        assertThatThrownBy(() ->
            new RefreshSessionProperties(
                null,
                Duration.ofDays(30)
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "refreshTokenTtl must not be null"
            );
    }

    @Test
    void shouldRequireFamilyTtl() {
        assertThatThrownBy(() ->
            new RefreshSessionProperties(
                Duration.ofDays(7),
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "familyTtl must not be null"
            );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositiveRefreshTokenTtl(
        Duration invalidDuration
    ) {
        assertThatThrownBy(() ->
            new RefreshSessionProperties(
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
            new RefreshSessionProperties(
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

    private static Stream<Duration>
    nonPositiveDurations() {
        return Stream.of(
            Duration.ZERO,
            Duration.ofSeconds(-1)
        );
    }
}
