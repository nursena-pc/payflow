package com.nursena.payflow.eventprocessing.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in.ClaimKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import org.springframework.transaction.annotation.Transactional;

public class ClaimKafkaDeadLetterRecordService
    implements ClaimKafkaDeadLetterRecordUseCase {

    private static final int
        MAX_WORKER_ID_LENGTH = 200;

    private final KafkaDeadLetterReplayRepositoryPort
        repository;

    private final String workerId;

    private final Duration leaseDuration;

    private final int maxReplayAttempts;

    private final Clock clock;

    public ClaimKafkaDeadLetterRecordService(
        KafkaDeadLetterReplayRepositoryPort
            repository,
        String workerId,
        Duration leaseDuration,
        int maxReplayAttempts,
        Clock clock
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );

        this.workerId =
            validateWorkerId(workerId);

        this.leaseDuration =
            validateLeaseDuration(
                leaseDuration
            );

        this.maxReplayAttempts =
            validateMaximumAttempts(
                maxReplayAttempts
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Override
    @Transactional
    public ClaimKafkaDeadLetterRecordResult claim(
        ClaimKafkaDeadLetterRecordCommand command
    ) {
        Objects.requireNonNull(
            command,
            "command must not be null"
        );

        Instant claimedAt =
            currentTime();

        return repository
            .tryClaim(
                command.recordId(),
                workerId,
                claimedAt,
                leaseDuration,
                maxReplayAttempts
            )
            .map(
                ClaimKafkaDeadLetterRecordResult
                    ::claimed
            )
            .orElseGet(
                ClaimKafkaDeadLetterRecordResult
                    ::notClaimable
            );
    }

    private Instant currentTime() {
        return clock
            .instant()
            .truncatedTo(
                ChronoUnit.MICROS
            );
    }

    private static String validateWorkerId(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                "workerId must not be blank."
            );
        }

        if (
            value.length()
                > MAX_WORKER_ID_LENGTH
        ) {
            throw new IllegalArgumentException(
                "workerId must not exceed "
                    + MAX_WORKER_ID_LENGTH
                    + " characters."
            );
        }

        return value;
    }

    private static Duration
    validateLeaseDuration(
        Duration value
    ) {
        Objects.requireNonNull(
            value,
            "leaseDuration must not be null"
        );

        if (
            value.isZero()
                || value.isNegative()
        ) {
            throw new IllegalArgumentException(
                "leaseDuration must be positive."
            );
        }

        return value;
    }

    private static int validateMaximumAttempts(
        int value
    ) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                "maxReplayAttempts must be "
                    + "positive."
            );
        }

        return value;
    }
}
