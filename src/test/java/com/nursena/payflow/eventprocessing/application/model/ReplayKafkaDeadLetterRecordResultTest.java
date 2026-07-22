package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReplayKafkaDeadLetterRecordResultTest {

    @Test
    void shouldCreateNotFoundResult() {
        ReplayKafkaDeadLetterRecordResult result =
            ReplayKafkaDeadLetterRecordResult
                .notFound();

        assertThat(result.outcome())
            .isEqualTo(
                ReplayKafkaDeadLetterRecordResult
                    .Outcome.NOT_FOUND
            );

        assertThat(result.isNotFound())
            .isTrue();

        assertOtherOutcomesAreFalse(result);
    }

    @Test
    void shouldCreateNotClaimableResult() {
        ReplayKafkaDeadLetterRecordResult result =
            ReplayKafkaDeadLetterRecordResult
                .notClaimable();

        assertThat(result.outcome())
            .isEqualTo(
                ReplayKafkaDeadLetterRecordResult
                    .Outcome.NOT_CLAIMABLE
            );

        assertThat(result.isNotClaimable())
            .isTrue();

        assertThat(result.isNotFound())
            .isFalse();

        assertThat(result.isReplayed())
            .isFalse();

        assertThat(result.isReplayFailed())
            .isFalse();

        assertThat(result.isUnresolved())
            .isFalse();
    }

    @Test
    void shouldCreateReplayedResult() {
        ReplayKafkaDeadLetterRecordResult result =
            ReplayKafkaDeadLetterRecordResult
                .replayed();

        assertThat(result.outcome())
            .isEqualTo(
                ReplayKafkaDeadLetterRecordResult
                    .Outcome.REPLAYED
            );

        assertThat(result.isReplayed())
            .isTrue();

        assertThat(result.isNotFound())
            .isFalse();

        assertThat(result.isNotClaimable())
            .isFalse();

        assertThat(result.isReplayFailed())
            .isFalse();

        assertThat(result.isUnresolved())
            .isFalse();
    }

    @Test
    void shouldCreateReplayFailedResult() {
        ReplayKafkaDeadLetterRecordResult result =
            ReplayKafkaDeadLetterRecordResult
                .replayFailed();

        assertThat(result.outcome())
            .isEqualTo(
                ReplayKafkaDeadLetterRecordResult
                    .Outcome.REPLAY_FAILED
            );

        assertThat(result.isReplayFailed())
            .isTrue();

        assertThat(result.isNotFound())
            .isFalse();

        assertThat(result.isNotClaimable())
            .isFalse();

        assertThat(result.isReplayed())
            .isFalse();

        assertThat(result.isUnresolved())
            .isFalse();
    }

    @Test
    void shouldCreateUnresolvedResult() {
        ReplayKafkaDeadLetterRecordResult result =
            ReplayKafkaDeadLetterRecordResult
                .unresolved();

        assertThat(result.outcome())
            .isEqualTo(
                ReplayKafkaDeadLetterRecordResult
                    .Outcome.UNRESOLVED
            );

        assertThat(result.isUnresolved())
            .isTrue();

        assertThat(result.isNotFound())
            .isFalse();

        assertThat(result.isNotClaimable())
            .isFalse();

        assertThat(result.isReplayed())
            .isFalse();

        assertThat(result.isReplayFailed())
            .isFalse();
    }

    @Test
    void shouldRequireOutcome() {
        assertThatThrownBy(() ->
            new ReplayKafkaDeadLetterRecordResult(
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "outcome must not be null"
            );
    }

    private static void
    assertOtherOutcomesAreFalse(
        ReplayKafkaDeadLetterRecordResult result
    ) {
        assertThat(result.isNotClaimable())
            .isFalse();

        assertThat(result.isReplayed())
            .isFalse();

        assertThat(result.isReplayFailed())
            .isFalse();

        assertThat(result.isUnresolved())
            .isFalse();
    }
}
