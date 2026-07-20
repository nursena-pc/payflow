package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import org.junit.jupiter.api.Test;

class ProcessTransferCompletedEventCommandTest {

    @Test
    void shouldCreateValidCommand() {
        ProcessTransferCompletedEventCommand command =
            new ProcessTransferCompletedEventCommand(
                event(),
                TransferCompletedEvent.TYPE,
                0,
                25L
            );

        assertThat(command.event())
            .isEqualTo(event());

        assertThat(command.topic())
            .isEqualTo(
                TransferCompletedEvent.TYPE
            );

        assertThat(command.partitionNumber())
            .isZero();

        assertThat(command.recordOffset())
            .isEqualTo(25L);
    }

    @Test
    void shouldRejectInvalidKafkaMetadata() {
        assertThatThrownBy(
            () -> new ProcessTransferCompletedEventCommand(
                event(),
                " ",
                0,
                25L
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "topic must not be blank."
            );

        assertThatThrownBy(
            () -> new ProcessTransferCompletedEventCommand(
                event(),
                TransferCompletedEvent.TYPE,
                -1,
                25L
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "partitionNumber must not be negative."
            );

        assertThatThrownBy(
            () -> new ProcessTransferCompletedEventCommand(
                event(),
                TransferCompletedEvent.TYPE,
                0,
                -1L
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "recordOffset must not be negative."
            );
    }

    private static TransferCompletedEvent event() {
        return new TransferCompletedEvent(
            UUID.fromString(
                "50000000-0000-0000-0000-000000000501"
            ),
            TransferCompletedEvent.TYPE,
            TransferCompletedEvent.VERSION,
            Instant.parse(
                "2026-07-20T20:00:00Z"
            ),
            UUID.fromString(
                "60000000-0000-0000-0000-000000000501"
            ),
            UUID.fromString(
                "70000000-0000-0000-0000-000000000501"
            ),
            UUID.fromString(
                "70000000-0000-0000-0000-000000000502"
            ),
            "125.50",
            "TRY"
        );
    }
}
