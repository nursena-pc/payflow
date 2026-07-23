package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class OperatorDiscardKafkaDeadLetterRecordCommandTest {

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002201"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002202"
        );

    @Test
    void shouldCreateCommand() {
        OperatorDiscardKafkaDeadLetterRecordCommand
            command =
            new OperatorDiscardKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        assertThat(command.operatorId())
            .isEqualTo(OPERATOR_ID);

        assertThat(command.recordId())
            .isEqualTo(RECORD_ID);
    }

    @Test
    void shouldRequireIdentifiers() {
        assertThatThrownBy(() ->
            new OperatorDiscardKafkaDeadLetterRecordCommand(
                null,
                RECORD_ID
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "operatorId must not be null"
            );

        assertThatThrownBy(() ->
            new OperatorDiscardKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
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
