package com.nursena.payflow.eventprocessing.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class KafkaDeadLetterReplayPublishingExceptionTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001801"
        );

    @Test
    void shouldExposeSafePublicationContext() {
        IllegalStateException cause =
            new IllegalStateException(
                "Broker failure."
            );

        KafkaDeadLetterReplayPublishingException
            exception =
            new KafkaDeadLetterReplayPublishingException(
                RECORD_ID,
                "wallet.transfer.completed",
                "Kafka broker rejected the send.",
                cause
            );

        assertThat(exception.recordId())
            .isEqualTo(RECORD_ID);

        assertThat(exception.topic())
            .isEqualTo(
                "wallet.transfer.completed"
            );

        assertThat(exception)
            .hasMessageContaining(
                RECORD_ID.toString()
            )
            .hasMessageContaining(
                "wallet.transfer.completed"
            )
            .hasMessageContaining(
                "Kafka broker rejected the send."
            )
            .hasCause(cause);
    }

    @Test
    void shouldValidateExceptionContext() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayPublishingException(
                null,
                "wallet.transfer.completed",
                "Failure.",
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "recordId must not be null"
            );

        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayPublishingException(
                RECORD_ID,
                " ",
                "Failure.",
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "topic must not be blank."
            );

        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayPublishingException(
                RECORD_ID,
                "wallet.transfer.completed",
                " ",
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "reason must not be blank."
            );
    }
}
