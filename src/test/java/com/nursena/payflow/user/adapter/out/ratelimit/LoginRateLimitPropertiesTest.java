package com.nursena.payflow.user.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LoginRateLimitPropertiesTest {

    @Test
    void shouldRetainValidConfiguration() {
        LoginRateLimitProperties properties =
            new LoginRateLimitProperties(
                true,
                Duration.ofMinutes(15),
                5,
                20
            );

        assertThat(properties.enabled())
            .isTrue();

        assertThat(properties.window())
            .isEqualTo(
                Duration.ofMinutes(15)
            );

        assertThat(properties.identityLimit())
            .isEqualTo(5);

        assertThat(properties.clientLimit())
            .isEqualTo(20);
    }

    @Test
    void shouldRequireWindow() {
        assertThatThrownBy(() ->
            new LoginRateLimitProperties(
                true,
                null,
                5,
                20
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "window must not be null"
            );
    }

    @Test
    void shouldRejectSubSecondWindow() {
        assertThatThrownBy(() ->
            new LoginRateLimitProperties(
                true,
                Duration.ofMillis(999),
                5,
                20
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "window must be at least one second"
            );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveLimits")
    void shouldRejectNonPositiveIdentityLimit(
        int invalidLimit
    ) {
        assertThatThrownBy(() ->
            new LoginRateLimitProperties(
                true,
                Duration.ofMinutes(15),
                invalidLimit,
                20
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "identityLimit must be positive"
            );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveLimits")
    void shouldRejectNonPositiveClientLimit(
        int invalidLimit
    ) {
        assertThatThrownBy(() ->
            new LoginRateLimitProperties(
                true,
                Duration.ofMinutes(15),
                5,
                invalidLimit
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "clientLimit must be positive"
            );
    }

    private static Stream<Integer>
    nonPositiveLimits() {
        return Stream.of(
            0,
            -1
        );
    }
}
