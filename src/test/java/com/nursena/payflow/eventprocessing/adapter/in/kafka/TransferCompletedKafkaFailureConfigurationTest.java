package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

class TransferCompletedKafkaFailureConfigurationTest {

    @Test
    void shouldCreateBoundedExponentialBackOff() {
        ExponentialBackOffWithMaxRetries backOff =
            TransferCompletedKafkaFailureConfiguration
                .retryBackOff(properties());

        assertThat(backOff.getMaxRetries())
            .isEqualTo(3);

        assertThat(backOff.getInitialInterval())
            .isEqualTo(500L);

        assertThat(backOff.getMultiplier())
            .isEqualTo(2.0);

        assertThat(backOff.getMaxInterval())
            .isEqualTo(5_000L);
    }

    @Test
    void shouldClassifyPermanentFailuresAsNonRetryable() {
        assertThat(
            TransferCompletedKafkaFailureConfiguration
                .nonRetryableExceptions()
        )
            .containsExactlyInAnyOrder(
                InvalidTransferCompletedKafkaRecordException
                    .class,
                TransferCompletedEventDeserializationException
                    .class,
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldRejectDeadLetterTopicEqualToSourceTopic() {
        assertThatThrownBy(
            () ->
                TransferCompletedKafkaFailureConfiguration
                    .validateDistinctTopics(
                        "wallet.transfer.completed",
                        "wallet.transfer.completed"
                    )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "deadLetterTopic must differ "
                    + "from source topic."
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
