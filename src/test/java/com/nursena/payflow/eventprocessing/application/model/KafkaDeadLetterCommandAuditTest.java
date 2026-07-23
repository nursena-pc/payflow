package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class KafkaDeadLetterCommandAuditTest {

    private static final UUID AUDIT_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002001"
        );

    private static final UUID COMMAND_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002002"
        );

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002003"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002004"
        );

    private static final Instant OCCURRED_AT =
        Instant.parse(
            "2026-07-23T13:45:20.123456789Z"
        );

    @Test
    void shouldCreateAttemptedAudit() {
        KafkaDeadLetterCommandAudit audit =
            KafkaDeadLetterCommandAudit.attempted(
                AUDIT_ID,
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                OCCURRED_AT
            );

        assertThat(audit.id())
            .isEqualTo(AUDIT_ID);

        assertThat(audit.commandId())
            .isEqualTo(COMMAND_ID);

        assertThat(audit.stage())
            .isEqualTo(
                KafkaDeadLetterCommandAuditStage
                    .ATTEMPTED
            );

        assertThat(audit.operatorId())
            .isEqualTo(OPERATOR_ID);

        assertThat(audit.deadLetterRecordId())
            .isEqualTo(RECORD_ID);

        assertThat(audit.commandType())
            .isEqualTo(
                KafkaDeadLetterCommandType.REPLAY
            );

        assertThat(audit.outcome())
            .isNull();

        assertThat(audit.errorCode())
            .isNull();

        assertThat(audit.occurredAt())
            .isEqualTo(
                Instant.parse(
                    "2026-07-23T13:45:20.123456Z"
                )
            );
    }

    @Test
    void shouldCreateSuccessfulCompletedAudit() {
        KafkaDeadLetterCommandAudit audit =
            KafkaDeadLetterCommandAudit.completed(
                AUDIT_ID,
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType
                    .DISCARD,
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARDED,
                OCCURRED_AT
            );

        assertThat(audit.stage())
            .isEqualTo(
                KafkaDeadLetterCommandAuditStage
                    .COMPLETED
            );

        assertThat(audit.outcome())
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARDED
            );

        assertThat(audit.errorCode())
            .isNull();
    }

    @Test
    void shouldDeriveSafeErrorCode() {
        KafkaDeadLetterCommandAudit audit =
            KafkaDeadLetterCommandAudit.completed(
                AUDIT_ID,
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_FAILED,
                OCCURRED_AT
            );

        assertThat(audit.errorCode())
            .isEqualTo(
                "KAFKA_DEAD_LETTER_REPLAY_FAILED"
            );
    }

    @Test
    void shouldAllowInternalFailureForBothCommands() {
        KafkaDeadLetterCommandAudit replayAudit =
            completedInternalFailure(
                KafkaDeadLetterCommandType.REPLAY
            );

        KafkaDeadLetterCommandAudit discardAudit =
            completedInternalFailure(
                KafkaDeadLetterCommandType.DISCARD
            );

        assertThat(replayAudit.errorCode())
            .isEqualTo(
                "KAFKA_DEAD_LETTER_COMMAND_"
                    + "INTERNAL_FAILURE"
            );

        assertThat(discardAudit.errorCode())
            .isEqualTo(replayAudit.errorCode());
    }

    @Test
    void shouldRejectOutcomeForAttemptedAudit() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterCommandAudit(
                AUDIT_ID,
                COMMAND_ID,
                KafkaDeadLetterCommandAuditStage
                    .ATTEMPTED,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAYED,
                null,
                OCCURRED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "ATTEMPTED audit must not have "
                    + "an outcome or errorCode."
            );
    }

    @Test
    void shouldRequireOutcomeForCompletedAudit() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterCommandAudit(
                AUDIT_ID,
                COMMAND_ID,
                KafkaDeadLetterCommandAuditStage
                    .COMPLETED,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                null,
                null,
                OCCURRED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "COMPLETED audit must have an outcome."
            );
    }

    @Test
    void shouldRejectOutcomeForDifferentCommand() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterCommandAudit(
                AUDIT_ID,
                COMMAND_ID,
                KafkaDeadLetterCommandAuditStage
                    .COMPLETED,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.DISCARD,
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAYED,
                null,
                OCCURRED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "outcome must be compatible "
                    + "with commandType."
            );
    }

    @Test
    void shouldRejectInconsistentErrorCode() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterCommandAudit(
                AUDIT_ID,
                COMMAND_ID,
                KafkaDeadLetterCommandAuditStage
                    .COMPLETED,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_FAILED,
                "SENSITIVE_EXCEPTION_DETAIL",
                OCCURRED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "errorCode must match the "
                    + "selected outcome."
            );
    }

    @Test
    void shouldRequireIdentifiersAndTimestamp() {
        assertThatThrownBy(() ->
            KafkaDeadLetterCommandAudit.attempted(
                null,
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                OCCURRED_AT
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "id must not be null"
            );

        assertThatThrownBy(() ->
            KafkaDeadLetterCommandAudit.attempted(
                AUDIT_ID,
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "occurredAt must not be null"
            );
    }

    private static KafkaDeadLetterCommandAudit
    completedInternalFailure(
        KafkaDeadLetterCommandType commandType
    ) {
        return KafkaDeadLetterCommandAudit.completed(
            AUDIT_ID,
            COMMAND_ID,
            OPERATOR_ID,
            RECORD_ID,
            commandType,
            KafkaDeadLetterCommandAuditOutcome
                .INTERNAL_FAILURE,
            OCCURRED_AT
        );
    }
}
