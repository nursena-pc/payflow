package com.nursena.payflow.eventprocessing.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in.ClaimKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in.ReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayLifecyclePort;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayPublisherPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplayKafkaDeadLetterRecordService
    implements ReplayKafkaDeadLetterRecordUseCase {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            ReplayKafkaDeadLetterRecordService.class
        );

    private static final int
        MAX_ERROR_LENGTH = 1000;

    private final ClaimKafkaDeadLetterRecordUseCase
        claimUseCase;

    private final KafkaDeadLetterReplayPublisherPort
        publisherPort;

    private final KafkaDeadLetterReplayLifecyclePort
        lifecyclePort;

    private final Clock clock;

    public ReplayKafkaDeadLetterRecordService(
        ClaimKafkaDeadLetterRecordUseCase
            claimUseCase,
        KafkaDeadLetterReplayPublisherPort
            publisherPort,
        KafkaDeadLetterReplayLifecyclePort
            lifecyclePort,
        Clock clock
    ) {
        this.claimUseCase =
            Objects.requireNonNull(
                claimUseCase,
                "claimUseCase must not be null"
            );

        this.publisherPort =
            Objects.requireNonNull(
                publisherPort,
                "publisherPort must not be null"
            );

        this.lifecyclePort =
            Objects.requireNonNull(
                lifecyclePort,
                "lifecyclePort must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Override
    public ReplayKafkaDeadLetterRecordResult replay(
        ReplayKafkaDeadLetterRecordCommand command
    ) {
        ReplayKafkaDeadLetterRecordCommand
            validatedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        ClaimKafkaDeadLetterRecordResult
            claimResult =
            claimUseCase.claim(
                new ClaimKafkaDeadLetterRecordCommand(
                    validatedCommand.recordId()
                )
            );

        if (claimResult.isNotFound()) {
            return ReplayKafkaDeadLetterRecordResult
                .notFound();
        }

        if (!claimResult.isClaimed()) {
            return ReplayKafkaDeadLetterRecordResult
                .notClaimable();
        }

        KafkaDeadLetterRecord record =
            validateClaimedRecord(
                validatedCommand,
                claimResult
            );

        try {
            publisherPort.publish(record);
        } catch (RuntimeException exception) {
            return persistReplayFailure(
                record,
                exception
            );
        }

        return persistReplaySuccess(record);
    }

    private ReplayKafkaDeadLetterRecordResult
    persistReplaySuccess(
        KafkaDeadLetterRecord record
    ) {
        try {
            boolean transitioned =
                lifecyclePort.tryMarkReplayed(
                    record.id(),
                    record.replayLeaseOwner(),
                    currentTime()
                );

            if (transitioned) {
                return ReplayKafkaDeadLetterRecordResult
                    .replayed();
            }

            logRejectedLifecycleTransition(
                record,
                "REPLAYED"
            );

            return ReplayKafkaDeadLetterRecordResult
                .unresolved();
        } catch (RuntimeException exception) {
            logLifecyclePersistenceFailure(
                record,
                "REPLAYED",
                exception
            );

            return ReplayKafkaDeadLetterRecordResult
                .unresolved();
        }
    }

    private ReplayKafkaDeadLetterRecordResult
    persistReplayFailure(
        KafkaDeadLetterRecord record,
        RuntimeException publishingFailure
    ) {
        try {
            boolean transitioned =
                lifecyclePort
                    .tryMarkReplayFailed(
                        record.id(),
                        record.replayLeaseOwner(),
                        currentTime(),
                        failureMessage(
                            publishingFailure
                        )
                    );

            if (transitioned) {
                return ReplayKafkaDeadLetterRecordResult
                    .replayFailed();
            }

            logRejectedLifecycleTransition(
                record,
                "REPLAY_FAILED"
            );

            return ReplayKafkaDeadLetterRecordResult
                .unresolved();
        } catch (RuntimeException exception) {
            logLifecyclePersistenceFailure(
                record,
                "REPLAY_FAILED",
                exception
            );

            return ReplayKafkaDeadLetterRecordResult
                .unresolved();
        }
    }

    private static KafkaDeadLetterRecord
    validateClaimedRecord(
        ReplayKafkaDeadLetterRecordCommand command,
        ClaimKafkaDeadLetterRecordResult result
    ) {
        KafkaDeadLetterRecord record =
            Objects.requireNonNull(
                result.record(),
                "CLAIMED result record "
                    + "must not be null"
            );

        if (!record.id().equals(
            command.recordId()
        )) {
            throw new IllegalStateException(
                "Claimed Kafka dead-letter "
                    + "record identifier does not "
                    + "match the replay command."
            );
        }

        if (
            record.status()
                != KafkaDeadLetterRecordStatus
                .REPLAYING
        ) {
            throw new IllegalStateException(
                "Claimed Kafka dead-letter "
                    + "record must be REPLAYING."
            );
        }

        if (
            record.replayLeaseOwner() == null
                || record.replayLeaseOwner()
                .isBlank()
        ) {
            throw new IllegalStateException(
                "Claimed Kafka dead-letter "
                    + "record must have a replay "
                    + "lease owner."
            );
        }

        return record;
    }

    private static String failureMessage(
        RuntimeException exception
    ) {
        String exceptionType =
            exception.getClass()
                .getSimpleName();

        if (exceptionType.isBlank()) {
            exceptionType =
                exception.getClass()
                    .getName();
        }

        String detail =
            exception.getMessage();

        String error =
            detail == null
                || detail.isBlank()
                ? exceptionType
                : exceptionType
                + ": "
                + detail;

        if (
            error.length()
                <= MAX_ERROR_LENGTH
        ) {
            return error;
        }

        return error.substring(
            0,
            MAX_ERROR_LENGTH
        );
    }

    private static void
    logRejectedLifecycleTransition(
        KafkaDeadLetterRecord record,
        String intendedOutcome
    ) {
        LOGGER.error(
            "Kafka dead-letter replay lifecycle "
                + "transition was rejected. "
                + "recordId={}, workerId={}, "
                + "intendedOutcome={}",
            record.id(),
            record.replayLeaseOwner(),
            intendedOutcome
        );
    }

    private static void
    logLifecyclePersistenceFailure(
        KafkaDeadLetterRecord record,
        String intendedOutcome,
        RuntimeException exception
    ) {
        LOGGER.error(
            "Kafka dead-letter replay outcome "
                + "could not be persisted. "
                + "recordId={}, workerId={}, "
                + "intendedOutcome={}",
            record.id(),
            record.replayLeaseOwner(),
            intendedOutcome,
            exception
        );
    }

    private Instant currentTime() {
        return clock.instant()
            .truncatedTo(
                ChronoUnit.MICROS
            );
    }
}
