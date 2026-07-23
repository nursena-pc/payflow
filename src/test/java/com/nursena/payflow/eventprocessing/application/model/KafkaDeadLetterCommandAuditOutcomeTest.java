package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KafkaDeadLetterCommandAuditOutcomeTest {

    @Test
    void shouldExposeSafeErrorCodes() {
        assertThat(
            KafkaDeadLetterCommandAuditOutcome
                .REPLAYED.safeErrorCode()
        )
            .isNull();

        assertThat(
            KafkaDeadLetterCommandAuditOutcome
                .REPLAY_NOT_CLAIMABLE
                .safeErrorCode()
        )
            .isEqualTo(
                "KAFKA_DEAD_LETTER_RECORD_"
                    + "NOT_CLAIMABLE"
            );

        assertThat(
            KafkaDeadLetterCommandAuditOutcome
                .INTERNAL_FAILURE
                .safeErrorCode()
        )
            .isEqualTo(
                "KAFKA_DEAD_LETTER_COMMAND_"
                    + "INTERNAL_FAILURE"
            );
    }

    @Test
    void shouldEnforceCommandCompatibility() {
        assertThat(
            KafkaDeadLetterCommandAuditOutcome
                .REPLAYED.supports(
                    KafkaDeadLetterCommandType
                        .REPLAY
                )
        )
            .isTrue();

        assertThat(
            KafkaDeadLetterCommandAuditOutcome
                .REPLAYED.supports(
                    KafkaDeadLetterCommandType
                        .DISCARD
                )
        )
            .isFalse();

        assertThat(
            KafkaDeadLetterCommandAuditOutcome
                .INTERNAL_FAILURE.supports(
                    KafkaDeadLetterCommandType
                        .REPLAY
                )
        )
            .isTrue();

        assertThat(
            KafkaDeadLetterCommandAuditOutcome
                .INTERNAL_FAILURE.supports(
                    KafkaDeadLetterCommandType
                        .DISCARD
                )
        )
            .isTrue();
    }

    @Test
    void shouldRequireCandidateCommandType() {
        assertThatThrownBy(() ->
            KafkaDeadLetterCommandAuditOutcome
                .REPLAYED.supports(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "candidate must not be null"
            );
    }
}
