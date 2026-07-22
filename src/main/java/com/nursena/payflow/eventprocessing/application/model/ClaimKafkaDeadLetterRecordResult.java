package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;

import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;

public record ClaimKafkaDeadLetterRecordResult(
    Outcome outcome,
    KafkaDeadLetterRecord record
) {

    public ClaimKafkaDeadLetterRecordResult {
        outcome =
            Objects.requireNonNull(
                outcome,
                "outcome must not be null"
            );

        if (
            outcome == Outcome.CLAIMED
                && record == null
        ) {
            throw new IllegalArgumentException(
                "CLAIMED result must contain "
                    + "a record."
            );
        }

        if (
            outcome == Outcome.NOT_CLAIMABLE
                && record != null
        ) {
            throw new IllegalArgumentException(
                "NOT_CLAIMABLE result must not "
                    + "contain a record."
            );
        }
    }

    public static ClaimKafkaDeadLetterRecordResult
    claimed(
        KafkaDeadLetterRecord record
    ) {
        return new ClaimKafkaDeadLetterRecordResult(
            Outcome.CLAIMED,
            Objects.requireNonNull(
                record,
                "record must not be null"
            )
        );
    }

    public static ClaimKafkaDeadLetterRecordResult
    notClaimable() {
        return new ClaimKafkaDeadLetterRecordResult(
            Outcome.NOT_CLAIMABLE,
            null
        );
    }

    public boolean isClaimed() {
        return outcome == Outcome.CLAIMED;
    }

    public enum Outcome {

        CLAIMED,
        NOT_CLAIMABLE
    }
}
