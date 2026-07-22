package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;

public record DiscardKafkaDeadLetterRecordResult(
    Outcome outcome
) {

    public DiscardKafkaDeadLetterRecordResult {
        outcome =
            Objects.requireNonNull(
                outcome,
                "outcome must not be null"
            );
    }

    public static DiscardKafkaDeadLetterRecordResult
    discarded() {
        return new DiscardKafkaDeadLetterRecordResult(
            Outcome.DISCARDED
        );
    }

    public static DiscardKafkaDeadLetterRecordResult
    alreadyDiscarded() {
        return new DiscardKafkaDeadLetterRecordResult(
            Outcome.ALREADY_DISCARDED
        );
    }

    public static DiscardKafkaDeadLetterRecordResult
    notFound() {
        return new DiscardKafkaDeadLetterRecordResult(
            Outcome.NOT_FOUND
        );
    }

    public static DiscardKafkaDeadLetterRecordResult
    notDiscardable() {
        return new DiscardKafkaDeadLetterRecordResult(
            Outcome.NOT_DISCARDABLE
        );
    }

    public boolean isDiscarded() {
        return outcome == Outcome.DISCARDED;
    }

    public boolean isAlreadyDiscarded() {
        return outcome
            == Outcome.ALREADY_DISCARDED;
    }

    public boolean isSuccessful() {
        return isDiscarded()
            || isAlreadyDiscarded();
    }

    public boolean isNotFound() {
        return outcome == Outcome.NOT_FOUND;
    }

    public boolean isNotDiscardable() {
        return outcome
            == Outcome.NOT_DISCARDABLE;
    }

    public enum Outcome {

        DISCARDED,
        ALREADY_DISCARDED,
        NOT_FOUND,
        NOT_DISCARDABLE
    }
}
