package com.nursena.payflow.eventprocessing.domain.exception;

import java.util.Objects;

public final class KafkaDeadLetterCommandAuditException
    extends RuntimeException {

    private final Reason reason;

    private KafkaDeadLetterCommandAuditException(
        Reason reason,
        String message,
        RuntimeException cause
    ) {
        super(
            message,
            Objects.requireNonNull(
                cause,
                "cause must not be null"
            )
        );

        this.reason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );
    }

    public static KafkaDeadLetterCommandAuditException
    attemptPersistenceFailed(
        RuntimeException cause
    ) {
        return new KafkaDeadLetterCommandAuditException(
            Reason.ATTEMPT_PERSISTENCE_FAILED,
            "Kafka dead-letter command audit "
                + "attempt could not be persisted.",
            cause
        );
    }

    public static KafkaDeadLetterCommandAuditException
    completionPersistenceFailed(
        RuntimeException cause
    ) {
        return new KafkaDeadLetterCommandAuditException(
            Reason.COMPLETION_PERSISTENCE_FAILED,
            "Kafka dead-letter command completion "
                + "could not be audited safely.",
            cause
        );
    }

    public static KafkaDeadLetterCommandAuditException
    commandInternalFailure(
        RuntimeException cause
    ) {
        return new KafkaDeadLetterCommandAuditException(
            Reason.COMMAND_INTERNAL_FAILURE,
            "Kafka dead-letter command failed "
                + "unexpectedly.",
            cause
        );
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {

        ATTEMPT_PERSISTENCE_FAILED,
        COMPLETION_PERSISTENCE_FAILED,
        COMMAND_INTERNAL_FAILURE
    }
}
