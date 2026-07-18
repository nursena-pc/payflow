package com.nursena.payflow.outbox.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class OutboxPollingPropertiesTest {

    @Test
    void shouldCreateValidProperties() {
        OutboxPollingProperties properties =
            properties();

        assertThat(properties.enabled())
            .isTrue();

        assertThat(properties.publisherId())
            .isEqualTo("publisher-1");

        assertThat(properties.batchSize())
            .isEqualTo(100);

        assertThat(properties.leaseDuration())
            .isEqualTo(Duration.ofSeconds(30));

        assertThat(properties.fixedDelay())
            .isEqualTo(Duration.ofSeconds(1));

        assertThat(properties.initialDelay())
            .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void shouldAllowZeroInitialDelay() {
        OutboxPollingProperties properties =
            new OutboxPollingProperties(
                true,
                "publisher-1",
                100,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ZERO
            );

        assertThat(properties.initialDelay())
            .isZero();
    }

    @Test
    void shouldRejectBlankPublisherId() {
        assertThatThrownBy(() ->
            new OutboxPollingProperties(
                true,
                " ",
                100,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "publisherId must not be blank."
            );
    }

    @Test
    void shouldRejectNonPositiveBatchSize() {
        assertThatThrownBy(() ->
            new OutboxPollingProperties(
                true,
                "publisher-1",
                0,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "batchSize must be positive."
            );
    }

    private static OutboxPollingProperties
    properties() {
        return new OutboxPollingProperties(
            true,
            "publisher-1",
            100,
            Duration.ofSeconds(30),
            Duration.ofSeconds(1),
            Duration.ofSeconds(5)
        );
    }
}
