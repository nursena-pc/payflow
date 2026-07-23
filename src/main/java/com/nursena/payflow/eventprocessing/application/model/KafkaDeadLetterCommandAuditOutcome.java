package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;

public enum KafkaDeadLetterCommandAuditOutcome {

    REPLAYED(
        KafkaDeadLetterCommandType.REPLAY,
        null
    ),

    REPLAY_NOT_FOUND(
        KafkaDeadLetterCommandType.REPLAY,
        "KAFKA_DEAD_LETTER_RECORD_NOT_FOUND"
    ),

    REPLAY_NOT_CLAIMABLE(
        KafkaDeadLetterCommandType.REPLAY,
        "KAFKA_DEAD_LETTER_RECORD_NOT_CLAIMABLE"
    ),

    REPLAY_FAILED(
        KafkaDeadLetterCommandType.REPLAY,
        "KAFKA_DEAD_LETTER_REPLAY_FAILED"
    ),

    REPLAY_UNRESOLVED(
        KafkaDeadLetterCommandType.REPLAY,
        "KAFKA_DEAD_LETTER_REPLAY_UNRESOLVED"
    ),

    DISCARDED(
        KafkaDeadLetterCommandType.DISCARD,
        null
    ),

    ALREADY_DISCARDED(
        KafkaDeadLetterCommandType.DISCARD,
        null
    ),

    DISCARD_NOT_FOUND(
        KafkaDeadLetterCommandType.DISCARD,
        "KAFKA_DEAD_LETTER_RECORD_NOT_FOUND"
    ),

    DISCARD_NOT_DISCARDABLE(
        KafkaDeadLetterCommandType.DISCARD,
        "KAFKA_DEAD_LETTER_RECORD_NOT_DISCARDABLE"
    ),

    INTERNAL_FAILURE(
        null,
        "KAFKA_DEAD_LETTER_COMMAND_INTERNAL_FAILURE"
    );

    private final KafkaDeadLetterCommandType
        commandType;

    private final String safeErrorCode;

    KafkaDeadLetterCommandAuditOutcome(
        KafkaDeadLetterCommandType commandType,
        String safeErrorCode
    ) {
        this.commandType = commandType;
        this.safeErrorCode = safeErrorCode;
    }

    public boolean supports(
        KafkaDeadLetterCommandType candidate
    ) {
        KafkaDeadLetterCommandType validatedCandidate =
            Objects.requireNonNull(
                candidate,
                "candidate must not be null"
            );

        return commandType == null
            || commandType == validatedCandidate;
    }

    public String safeErrorCode() {
        return safeErrorCode;
    }
}
