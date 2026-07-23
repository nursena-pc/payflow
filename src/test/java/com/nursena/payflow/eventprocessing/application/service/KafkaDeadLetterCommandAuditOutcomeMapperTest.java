package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordResult;
import org.junit.jupiter.api.Test;

class KafkaDeadLetterCommandAuditOutcomeMapperTest {

    @Test
    void shouldMapReplayOutcomes() {
        assertThat(
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromReplay(
                    ReplayKafkaDeadLetterRecordResult
                        .replayed()
                )
        )
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAYED
            );

        assertThat(
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromReplay(
                    ReplayKafkaDeadLetterRecordResult
                        .notFound()
                )
        )
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_NOT_FOUND
            );

        assertThat(
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromReplay(
                    ReplayKafkaDeadLetterRecordResult
                        .notClaimable()
                )
        )
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_NOT_CLAIMABLE
            );

        assertThat(
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromReplay(
                    ReplayKafkaDeadLetterRecordResult
                        .replayFailed()
                )
        )
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_FAILED
            );

        assertThat(
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromReplay(
                    ReplayKafkaDeadLetterRecordResult
                        .unresolved()
                )
        )
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_UNRESOLVED
            );
    }

    @Test
    void shouldMapDiscardOutcomes() {
        assertThat(
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromDiscard(
                    DiscardKafkaDeadLetterRecordResult
                        .discarded()
                )
        )
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARDED
            );

        assertThat(
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromDiscard(
                    DiscardKafkaDeadLetterRecordResult
                        .alreadyDiscarded()
                )
        )
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .ALREADY_DISCARDED
            );

        assertThat(
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromDiscard(
                    DiscardKafkaDeadLetterRecordResult
                        .notFound()
                )
        )
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARD_NOT_FOUND
            );

        assertThat(
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromDiscard(
                    DiscardKafkaDeadLetterRecordResult
                        .notDiscardable()
                )
        )
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARD_NOT_DISCARDABLE
            );
    }

    @Test
    void shouldRequireReplayResult() {
        assertThatThrownBy(() ->
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromReplay(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "result must not be null"
            );
    }

    @Test
    void shouldRequireDiscardResult() {
        assertThatThrownBy(() ->
            KafkaDeadLetterCommandAuditOutcomeMapper
                .fromDiscard(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "result must not be null"
            );
    }
}
