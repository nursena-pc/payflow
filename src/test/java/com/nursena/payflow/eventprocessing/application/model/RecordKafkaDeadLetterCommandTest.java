package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RecordKafkaDeadLetterCommandTest {

    @Test
    void shouldCreateValidCommandWithNullableContent() {
        RecordKafkaDeadLetterCommand command =
            validCommand();

        assertThat(command.deadLetterTopic())
            .isEqualTo(
                "wallet.transfer.completed.dlt"
            );

        assertThat(command.deadLetterPartition())
            .isEqualTo(2);

        assertThat(command.deadLetterOffset())
            .isEqualTo(25L);

        assertThat(command.originalTopic())
            .isEqualTo(
                "wallet.transfer.completed"
            );

        assertThat(command.originalPartition())
            .isEqualTo(1);

        assertThat(command.originalOffset())
            .isEqualTo(42L);

        assertThat(command.recordKey())
            .isNull();

        assertThat(command.payload())
            .isNull();
    }

    @Test
    void shouldRejectBlankRequiredMetadata() {
        assertThatThrownBy(
            () -> new RecordKafkaDeadLetterCommand(
                "wallet.transfer.completed.dlt",
                2,
                25L,
                " ",
                1,
                42L,
                "payflow-transfer-completed-audit-v1",
                null,
                null,
                "java.lang.IllegalStateException",
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "originalTopic must not be blank."
            );
    }

    @Test
    void shouldRejectNegativeDeadLetterMetadata() {
        assertThatThrownBy(
            () -> new RecordKafkaDeadLetterCommand(
                "wallet.transfer.completed.dlt",
                -1,
                25L,
                "wallet.transfer.completed",
                1,
                42L,
                "payflow-transfer-completed-audit-v1",
                null,
                null,
                "java.lang.IllegalStateException",
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "deadLetterPartition "
                    + "must not be negative."
            );

        assertThatThrownBy(
            () -> new RecordKafkaDeadLetterCommand(
                "wallet.transfer.completed.dlt",
                2,
                -1L,
                "wallet.transfer.completed",
                1,
                42L,
                "payflow-transfer-completed-audit-v1",
                null,
                null,
                "java.lang.IllegalStateException",
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "deadLetterOffset "
                    + "must not be negative."
            );
    }

    @Test
    void shouldRejectNegativeOriginalMetadata() {
        assertThatThrownBy(
            () -> new RecordKafkaDeadLetterCommand(
                "wallet.transfer.completed.dlt",
                2,
                25L,
                "wallet.transfer.completed",
                1,
                -1L,
                "payflow-transfer-completed-audit-v1",
                null,
                null,
                "java.lang.IllegalStateException",
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "originalOffset must not be negative."
            );
    }

    private static RecordKafkaDeadLetterCommand
    validCommand() {
        return new RecordKafkaDeadLetterCommand(
            "wallet.transfer.completed.dlt",
            2,
            25L,
            "wallet.transfer.completed",
            1,
            42L,
            "payflow-transfer-completed-audit-v1",
            null,
            null,
            "java.lang.IllegalStateException",
            "Temporary processing failure."
        );
    }
}
