package com.nursena.payflow.eventprocessing.domain.exception;

import java.util.Objects;
import java.util.UUID;

public final class KafkaDeadLetterCommandException
    extends RuntimeException {

    private final Reason reason;

    private final UUID recordId;

    private KafkaDeadLetterCommandException(
        UUID recordId,
        Reason reason
    ) {
        super(
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            ).message()
        );

        this.recordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        this.reason = reason;
    }

    public static KafkaDeadLetterCommandException
    notClaimable(
        UUID recordId
    ) {
        return new KafkaDeadLetterCommandException(
            recordId,
            Reason.NOT_CLAIMABLE
        );
    }

    public static KafkaDeadLetterCommandException
    notDiscardable(
        UUID recordId
    ) {
        return new KafkaDeadLetterCommandException(
            recordId,
            Reason.NOT_DISCARDABLE
        );
    }

    public static KafkaDeadLetterCommandException
    replayFailed(
        UUID recordId
    ) {
        return new KafkaDeadLetterCommandException(
            recordId,
            Reason.REPLAY_FAILED
        );
    }

    public static KafkaDeadLetterCommandException
    replayUnresolved(
        UUID recordId
    ) {
        return new KafkaDeadLetterCommandException(
            recordId,
            Reason.REPLAY_UNRESOLVED
        );
    }

    public String getCode() {
        return reason.code();
    }

    public Reason getReason() {
        return reason;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public enum Reason {

        NOT_CLAIMABLE(
            "KAFKA_DEAD_LETTER_RECORD_NOT_CLAIMABLE",
            "Kafka dead-letter record cannot be "
                + "replayed in its current state."
        ),

        NOT_DISCARDABLE(
            "KAFKA_DEAD_LETTER_RECORD_NOT_DISCARDABLE",
            "Kafka dead-letter record cannot be "
                + "discarded in its current state."
        ),

        REPLAY_FAILED(
            "KAFKA_DEAD_LETTER_REPLAY_FAILED",
            "Kafka dead-letter replay publication "
                + "failed."
        ),

        REPLAY_UNRESOLVED(
            "KAFKA_DEAD_LETTER_REPLAY_UNRESOLVED",
            "Kafka dead-letter replay outcome "
                + "could not be resolved."
        );

        private final String code;

        private final String message;

        Reason(
            String code,
            String message
        ) {
            this.code = code;
            this.message = message;
        }

        String code() {
            return code;
        }

        String message() {
            return message;
        }
    }
}
