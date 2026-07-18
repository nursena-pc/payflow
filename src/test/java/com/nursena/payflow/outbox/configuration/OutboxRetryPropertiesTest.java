package com.nursena.payflow.outbox.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class OutboxRetryPropertiesTest {

    @Test
    void shouldCreateValidProperties() {
        OutboxRetryProperties properties =
            new OutboxRetryProperties(
                5,
                Duration.ofSeconds(10),
                Duration.ofMinutes(1)
            );

        assertThat(properties.maxAttempts())
            .isEqualTo(5);

        assertThat(properties.initialDelay())
            .isEqualTo(Duration.ofSeconds(10));

        assertThat(properties.maximumDelay())
            .isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldRejectNonPositiveMaximumAttempts() {
        assertThatThrownBy(() ->
            new OutboxRetryProperties(
                0,
                Duration.ofSeconds(10),
                Duration.ofMinutes(1)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "maxAttempts must be positive."
            );
    }

    @Test
    void shouldRejectMaximumDelayBelowInitialDelay() {
        assertThatThrownBy(() ->
            new OutboxRetryProperties(
                5,
                Duration.ofMinutes(1),
                Duration.ofSeconds(10)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "maximumDelay must not be less "
                    + "than initialDelay."
            );
    }
}
