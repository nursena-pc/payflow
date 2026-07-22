package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DiscardKafkaDeadLetterRecordCommandTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002201"
        );

    @Test
    void shouldCreateCommand() {
        DiscardKafkaDeadLetterRecordCommand command =
            new DiscardKafkaDeadLetterRecordCommand(
                RECORD_ID
            );

        assertThat(command.recordId())
            .isEqualTo(RECORD_ID);
    }

    @Test
    void shouldRequireRecordIdentifier() {
        assertThatThrownBy(
            () ->
                new DiscardKafkaDeadLetterRecordCommand(
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "recordId must not be null"
            );
    }
}
