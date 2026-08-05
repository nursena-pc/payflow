package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AccountActionCredentialPropertiesTest {

    @Test
    void shouldRetainPurposeSpecificDurations() {
        AccountActionCredentialProperties properties =
            new AccountActionCredentialProperties(
                Duration.ofHours(24),
                Duration.ofMinutes(30)
            );

        assertThat(properties.emailVerificationTtl())
            .isEqualTo(Duration.ofHours(24));
        assertThat(properties.passwordRecoveryTtl())
            .isEqualTo(Duration.ofMinutes(30));
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositiveEmailVerificationTtl(
        Duration invalidDuration
    ) {
        assertThatThrownBy(() ->
            new AccountActionCredentialProperties(
                invalidDuration,
                Duration.ofMinutes(30)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "emailVerificationTtl must be positive"
            );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositivePasswordRecoveryTtl(
        Duration invalidDuration
    ) {
        assertThatThrownBy(() ->
            new AccountActionCredentialProperties(
                Duration.ofHours(24),
                invalidDuration
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "passwordRecoveryTtl must be positive"
            );
    }

    private static Stream<Duration>
    nonPositiveDurations() {
        return Stream.of(
            Duration.ZERO,
            Duration.ofNanos(-1)
        );
    }
}
