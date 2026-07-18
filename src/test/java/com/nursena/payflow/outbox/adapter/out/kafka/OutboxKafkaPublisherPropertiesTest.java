package com.nursena.payflow.outbox.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class OutboxKafkaPublisherPropertiesTest {

    @Test
    void shouldCreateValidProperties() {
        OutboxKafkaPublisherProperties properties =
            new OutboxKafkaPublisherProperties(
                Duration.ofSeconds(10)
            );

        assertThat(properties.sendTimeout())
            .isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldRejectNonPositiveTimeout() {
        assertThatThrownBy(() ->
            new OutboxKafkaPublisherProperties(
                Duration.ZERO
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "sendTimeout must be positive."
            );
    }
}
