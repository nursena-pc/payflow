package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class TransferCompletedKafkaFailurePropertiesTest {

    @Test
    void shouldCreateValidFailureProperties() {
        TransferCompletedKafkaFailureProperties
            properties =
            properties();

        assertThat(
            properties.deadLetterTopic()
        )
            .isEqualTo(
                "wallet.transfer.completed.dlt"
            );

        assertThat(properties.maxRetries())
            .isEqualTo(3);

        assertThat(properties.initialDelay())
            .isEqualTo(
                Duration.ofMillis(500)
            );

        assertThat(properties.multiplier())
            .isEqualTo(2.0);

        assertThat(properties.maximumDelay())
            .isEqualTo(
                Duration.ofSeconds(5)
            );

        assertThat(properties.sendTimeout())
            .isEqualTo(
                Duration.ofSeconds(10)
            );
    }

    @Test
    void shouldRejectInvalidRetryConfiguration() {
        assertThatThrownBy(
            () -> new
                TransferCompletedKafkaFailureProperties(
                "wallet.transfer.completed.dlt",
                -1,
                Duration.ofMillis(500),
                2.0,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "maxRetries must not be negative."
            );

        assertThatThrownBy(
            () -> new
                TransferCompletedKafkaFailureProperties(
                "wallet.transfer.completed.dlt",
                3,
                Duration.ofMillis(500),
                0.9,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "multiplier must be greater than "
                    + "or equal to 1.0."
            );
    }

    @Test
    void shouldRejectMaximumDelayBelowInitialDelay() {
        assertThatThrownBy(
            () -> new
                TransferCompletedKafkaFailureProperties(
                "wallet.transfer.completed.dlt",
                3,
                Duration.ofSeconds(5),
                2.0,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "maximumDelay must be greater than "
                    + "or equal to initialDelay."
            );
    }

    @Test
    void shouldRejectInvalidDeadLetterTopic() {
        assertThatThrownBy(
            () -> new
                TransferCompletedKafkaFailureProperties(
                " ",
                3,
                Duration.ofMillis(500),
                2.0,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "deadLetterTopic must not be blank."
            );
    }

    private static
    TransferCompletedKafkaFailureProperties
    properties() {
        return new
            TransferCompletedKafkaFailureProperties(
            "wallet.transfer.completed.dlt",
            3,
            Duration.ofMillis(500),
            2.0,
            Duration.ofSeconds(5),
            Duration.ofSeconds(10)
        );
    }
}
