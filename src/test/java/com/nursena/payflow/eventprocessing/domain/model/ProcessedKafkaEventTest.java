package com.nursena.payflow.eventprocessing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProcessedKafkaEventTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000301"
        );

    private static final Instant PROCESSED_AT =
        Instant.parse(
            "2026-07-20T18:00:00Z"
        );

    @Test
    void shouldCreateValidProcessedEvent() {
        ProcessedKafkaEvent event =
            validEvent();

        assertThat(event.consumerName())
            .isEqualTo(
                "transfer-completed-notification"
            );

        assertThat(event.eventId())
            .isEqualTo(EVENT_ID);

        assertThat(event.eventVersion())
            .isEqualTo(1);

        assertThat(event.partitionNumber())
            .isZero();

        assertThat(event.recordOffset())
            .isEqualTo(15L);

        assertThat(event.processedAt())
            .isEqualTo(PROCESSED_AT);
    }

    @Test
    void shouldRejectBlankConsumerName() {
        assertThatThrownBy(
            () -> new ProcessedKafkaEvent(
                " ",
                EVENT_ID,
                "wallet.transfer.completed",
                1,
                "wallet.transfer.completed",
                0,
                15L,
                PROCESSED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "consumerName must not be blank."
            );
    }

    @Test
    void shouldRejectNonPositiveEventVersion() {
        assertThatThrownBy(
            () -> new ProcessedKafkaEvent(
                "transfer-completed-notification",
                EVENT_ID,
                "wallet.transfer.completed",
                0,
                "wallet.transfer.completed",
                0,
                15L,
                PROCESSED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "eventVersion must be positive."
            );
    }

    @Test
    void shouldRejectNegativeKafkaMetadata() {
        assertThatThrownBy(
            () -> new ProcessedKafkaEvent(
                "transfer-completed-notification",
                EVENT_ID,
                "wallet.transfer.completed",
                1,
                "wallet.transfer.completed",
                -1,
                15L,
                PROCESSED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "partitionNumber must not be negative."
            );

        assertThatThrownBy(
            () -> new ProcessedKafkaEvent(
                "transfer-completed-notification",
                EVENT_ID,
                "wallet.transfer.completed",
                1,
                "wallet.transfer.completed",
                0,
                -1L,
                PROCESSED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "recordOffset must not be negative."
            );
    }

    private static ProcessedKafkaEvent validEvent() {
        return new ProcessedKafkaEvent(
            "transfer-completed-notification",
            EVENT_ID,
            "wallet.transfer.completed",
            1,
            "wallet.transfer.completed",
            0,
            15L,
            PROCESSED_AT
        );
    }
}
