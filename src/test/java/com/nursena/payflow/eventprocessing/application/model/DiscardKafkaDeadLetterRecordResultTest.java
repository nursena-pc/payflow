package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiscardKafkaDeadLetterRecordResultTest {

    @Test
    void shouldCreateDiscardedResult() {
        DiscardKafkaDeadLetterRecordResult result =
            DiscardKafkaDeadLetterRecordResult
                .discarded();

        assertThat(result.outcome())
            .isEqualTo(
                DiscardKafkaDeadLetterRecordResult
                    .Outcome.DISCARDED
            );

        assertThat(result.isDiscarded())
            .isTrue();

        assertThat(result.isSuccessful())
            .isTrue();

        assertThat(result.isAlreadyDiscarded())
            .isFalse();

        assertThat(result.isNotFound())
            .isFalse();

        assertThat(result.isNotDiscardable())
            .isFalse();
    }

    @Test
    void shouldCreateAlreadyDiscardedResult() {
        DiscardKafkaDeadLetterRecordResult result =
            DiscardKafkaDeadLetterRecordResult
                .alreadyDiscarded();

        assertThat(result.isAlreadyDiscarded())
            .isTrue();

        assertThat(result.isSuccessful())
            .isTrue();

        assertThat(result.isDiscarded())
            .isFalse();
    }

    @Test
    void shouldCreateNotFoundResult() {
        DiscardKafkaDeadLetterRecordResult result =
            DiscardKafkaDeadLetterRecordResult
                .notFound();

        assertThat(result.isNotFound())
            .isTrue();

        assertThat(result.isSuccessful())
            .isFalse();

        assertThat(result.isNotDiscardable())
            .isFalse();
    }

    @Test
    void shouldCreateNotDiscardableResult() {
        DiscardKafkaDeadLetterRecordResult result =
            DiscardKafkaDeadLetterRecordResult
                .notDiscardable();

        assertThat(result.isNotDiscardable())
            .isTrue();

        assertThat(result.isSuccessful())
            .isFalse();

        assertThat(result.isNotFound())
            .isFalse();
    }

    @Test
    void shouldRequireOutcome() {
        assertThatThrownBy(
            () ->
                new DiscardKafkaDeadLetterRecordResult(
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
}
