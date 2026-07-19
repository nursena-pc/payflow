package com.nursena.payflow.outbox.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import com.nursena.payflow.outbox.application.model.OutboxRetryDecision;
import com.nursena.payflow.outbox.application.policy.OutboxRetryPolicy;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsCommand;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsResult;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsUseCase;
import com.nursena.payflow.outbox.application.port.out.OutboxEventClaimPort;
import com.nursena.payflow.outbox.application.port.out.OutboxEventLifecyclePort;
import com.nursena.payflow.outbox.application.port.out.OutboxMessagePublisherPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PublishOutboxEventsService
    implements PublishOutboxEventsUseCase {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            PublishOutboxEventsService.class
        );

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventClaimPort claimPort;
    private final OutboxMessagePublisherPort publisherPort;
    private final OutboxEventLifecyclePort lifecyclePort;
    private final OutboxRetryPolicy retryPolicy;
    private final Clock clock;

    public PublishOutboxEventsService(
        OutboxEventClaimPort claimPort,
        OutboxMessagePublisherPort publisherPort,
        OutboxEventLifecyclePort lifecyclePort,
        OutboxRetryPolicy retryPolicy,
        Clock clock
    ) {
        this.claimPort = claimPort;
        this.publisherPort = publisherPort;
        this.lifecyclePort = lifecyclePort;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Override
    public PublishOutboxEventsResult publishAvailable(
        PublishOutboxEventsCommand command
    ) {
        Objects.requireNonNull(
            command,
            "command must not be null"
        );

        Instant claimedAt = currentTime();

        List<OutboxEvent> claimedEvents =
            claimPort.claimAvailable(
                command.publisherId(),
                claimedAt,
                command.leaseDuration(),
                command.batchSize()
            );

        if (claimedEvents.isEmpty()) {
            return PublishOutboxEventsResult.empty();
        }

        int publishedCount = 0;
        int retriedCount = 0;
        int failedCount = 0;
        int unresolvedCount = 0;

        for (OutboxEvent event : claimedEvents) {
            PublishOutcome outcome =
                publishOne(
                    event,
                    command.publisherId()
                );

            switch (outcome) {
                case PUBLISHED ->
                    publishedCount++;

                case RETRIED ->
                    retriedCount++;

                case FAILED ->
                    failedCount++;

                case UNRESOLVED ->
                    unresolvedCount++;
            }
        }

        return new PublishOutboxEventsResult(
            claimedEvents.size(),
            publishedCount,
            retriedCount,
            failedCount,
            unresolvedCount
        );
    }

    private PublishOutcome publishOne(
        OutboxEvent event,
        String publisherId
    ) {
        try {
            publisherPort.publish(event);
        } catch (RuntimeException exception) {
            return handlePublishingFailure(
                event,
                publisherId,
                exception
            );
        }

        try {
            lifecyclePort.markPublished(
                event.id(),
                publisherId,
                currentTime()
            );

            return PublishOutcome.PUBLISHED;
        } catch (RuntimeException exception) {
            logUnresolvedOutcome(
                event,
                publisherId,
                exception
            );

            return PublishOutcome.UNRESOLVED;
        }
    }

    private PublishOutcome handlePublishingFailure(
        OutboxEvent event,
        String publisherId,
        RuntimeException publishingFailure
    ) {
        Instant failedAt = currentTime();

        try {
            OutboxRetryDecision decision =
                retryPolicy.decide(
                    event,
                    failedAt
                );

            String error =
                failureMessage(
                    publishingFailure
                );

            if (decision.shouldRetry()) {
                lifecyclePort.scheduleRetry(
                    event.id(),
                    publisherId,
                    failedAt,
                    decision.nextAvailableAt(),
                    error
                );

                return PublishOutcome.RETRIED;
            }

            lifecyclePort.markFailed(
                event.id(),
                publisherId,
                failedAt,
                error
            );

            return PublishOutcome.FAILED;
        } catch (RuntimeException outcomeFailure) {
            logUnresolvedOutcome(
                event,
                publisherId,
                outcomeFailure
            );

            return PublishOutcome.UNRESOLVED;
        }
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

        String detail = exception.getMessage();

        String error =
            detail == null
                || detail.isBlank()
                ? exceptionType
                : exceptionType + ": " + detail;

        if (error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }

        return error.substring(
            0,
            MAX_ERROR_LENGTH
        );
    }

    private static void logUnresolvedOutcome(
        OutboxEvent event,
        String publisherId,
        RuntimeException exception
    ) {
        LOGGER.error(
            "Outbox event outcome could not be persisted. "
                + "eventId={}, publisherId={}",
            event.id(),
            publisherId,
            exception
        );
    }

    private Instant currentTime() {
        return clock.instant()
            .truncatedTo(
                ChronoUnit.MICROS
            );
    }

    private enum PublishOutcome {
        PUBLISHED,
        RETRIED,
        FAILED,
        UNRESOLVED
    }
}
