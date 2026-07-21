package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ReplayKafkaDeadLetterRecordCommandTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001901"
        );

    @Test
    void shouldCreateCommand() {
        ReplayKafkaDeadLetterRecordCommand command =
            new ReplayKafkaDeadLetterRecordCommand(
                RECORD_ID
            );

        assertThat(command.recordId())
            .isEqualTo(RECORD_ID);
    }

    @Test
    void shouldRequireRecordIdentifier() {
        assertThatThrownBy(() ->
            new ReplayKafkaDeadLetterRecordCommand(
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
