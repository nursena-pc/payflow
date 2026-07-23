package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;

public record ReplayKafkaDeadLetterRecordResult(
    Outcome outcome
) {

    public ReplayKafkaDeadLetterRecordResult {
        outcome =
            Objects.requireNonNull(
                outcome,
                "outcome must not be null"
            );
    }

    public static ReplayKafkaDeadLetterRecordResult
    notFound() {
        return new ReplayKafkaDeadLetterRecordResult(
            Outcome.NOT_FOUND
        );
    }

    public static ReplayKafkaDeadLetterRecordResult
    notClaimable() {
        return new ReplayKafkaDeadLetterRecordResult(
            Outcome.NOT_CLAIMABLE
        );
    }

    public static ReplayKafkaDeadLetterRecordResult
    replayed() {
        return new ReplayKafkaDeadLetterRecordResult(
            Outcome.REPLAYED
        );
    }

    public static ReplayKafkaDeadLetterRecordResult
    replayFailed() {
        return new ReplayKafkaDeadLetterRecordResult(
            Outcome.REPLAY_FAILED
        );
    }

    public static ReplayKafkaDeadLetterRecordResult
    unresolved() {
        return new ReplayKafkaDeadLetterRecordResult(
            Outcome.UNRESOLVED
        );
    }

    public boolean isNotFound() {
        return outcome == Outcome.NOT_FOUND;
    }

    public boolean isNotClaimable() {
        return outcome == Outcome.NOT_CLAIMABLE;
    }

    public boolean isReplayed() {
        return outcome == Outcome.REPLAYED;
    }

    public boolean isReplayFailed() {
        return outcome == Outcome.REPLAY_FAILED;
    }

    public boolean isUnresolved() {
        return outcome == Outcome.UNRESOLVED;
    }

    public enum Outcome {

        NOT_FOUND,
        NOT_CLAIMABLE,
        REPLAYED,
        REPLAY_FAILED,
        UNRESOLVED
    }
}
