package com.nursena.payflow.eventprocessing.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class KafkaDeadLetterReplayPropertiesTest {

    @Test
    void shouldCreateValidProperties() {
        KafkaDeadLetterReplayProperties properties =
            new KafkaDeadLetterReplayProperties(
                "replay-worker-1",
                Duration.ofSeconds(30),
                3
            );

        assertThat(properties.workerId())
            .isEqualTo("replay-worker-1");

        assertThat(properties.leaseDuration())
            .isEqualTo(
                Duration.ofSeconds(30)
            );

        assertThat(properties.maxAttempts())
            .isEqualTo(3);
    }

    @Test
    void shouldRejectInvalidProperties() {
        assertThatThrownBy(
            () ->
                new KafkaDeadLetterReplayProperties(
                    " ",
                    Duration.ofSeconds(30),
                    3
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "workerId must not be blank."
            );

        assertThatThrownBy(
            () ->
                new KafkaDeadLetterReplayProperties(
                    "replay-worker-1",
                    Duration.ZERO,
                    3
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "leaseDuration must be positive."
            );

        assertThatThrownBy(
            () ->
                new KafkaDeadLetterReplayProperties(
                    "replay-worker-1",
                    Duration.ofSeconds(30),
                    0
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "maxAttempts must be positive."
            );
    }
}
