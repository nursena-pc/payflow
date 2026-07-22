package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
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

        assertThat(command.replayOriginId())
            .isNull();

        assertThat(command.replayAttemptBase())
            .isNull();
    }


    @Test
    void shouldAcceptCompleteReplayMetadata() {
        UUID replayOriginId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001305"
            );

        RecordKafkaDeadLetterCommand command =
            new RecordKafkaDeadLetterCommand(
                "wallet.transfer.completed.dlt",
                2,
                25L,
                "wallet.transfer.completed",
                1,
                42L,
                "payflow-transfer-completed-audit-v1",
                "transaction-id",
                "{}",
                "java.lang.IllegalStateException",
                "Temporary processing failure.",
                replayOriginId,
                2
            );

        assertThat(command.replayOriginId())
            .isEqualTo(replayOriginId);

        assertThat(command.replayAttemptBase())
            .isEqualTo(2);
    }

    @Test
    void shouldRejectPartialOrInvalidReplayMetadata() {
        UUID replayOriginId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001305"
            );

        assertThatThrownBy(
            () ->
                new RecordKafkaDeadLetterCommand(
                    "wallet.transfer.completed.dlt",
                    2,
                    25L,
                    "wallet.transfer.completed",
                    1,
                    42L,
                    "payflow-transfer-completed-audit-v1",
                    "transaction-id",
                    "{}",
                    "java.lang.IllegalStateException",
                    null,
                    replayOriginId,
                    null
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "Replay origin id and attempt must "
                    + "either both be present "
                    + "or both be absent."
            );

        assertThatThrownBy(
            () ->
                new RecordKafkaDeadLetterCommand(
                    "wallet.transfer.completed.dlt",
                    2,
                    25L,
                    "wallet.transfer.completed",
                    1,
                    42L,
                    "payflow-transfer-completed-audit-v1",
                    "transaction-id",
                    "{}",
                    "java.lang.IllegalStateException",
                    null,
                    replayOriginId,
                    0
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "replayAttemptBase must be positive "
                    + "when replay metadata is present."
            );
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
