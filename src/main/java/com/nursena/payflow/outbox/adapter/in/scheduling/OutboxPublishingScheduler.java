package com.nursena.payflow.outbox.adapter.in.scheduling;

import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsCommand;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsResult;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

public final class OutboxPublishingScheduler {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            OutboxPublishingScheduler.class
        );

    private final PublishOutboxEventsUseCase
        publishOutboxEventsUseCase;

    private final PublishOutboxEventsCommand command;
    private final OutboxPollingMetrics metrics;
    private final OutboxBacklogMetrics backlogMetrics;

    public OutboxPublishingScheduler(
        PublishOutboxEventsUseCase
            publishOutboxEventsUseCase,
        OutboxPollingMetrics metrics,
        OutboxBacklogMetrics backlogMetrics,
        OutboxPollingProperties properties
    ) {
        this.publishOutboxEventsUseCase =
            Objects.requireNonNull(
                publishOutboxEventsUseCase,
                "publishOutboxEventsUseCase "
                    + "must not be null"
            );

        this.metrics =
            Objects.requireNonNull(
                metrics,
                "metrics must not be null"
            );

        this.backlogMetrics =
            Objects.requireNonNull(
                backlogMetrics,
                "backlogMetrics must not be null"
            );

        Objects.requireNonNull(
            properties,
            "properties must not be null"
        );

        this.command =
            new PublishOutboxEventsCommand(
                properties.publisherId(),
                properties.batchSize(),
                properties.leaseDuration()
            );
    }

    @Scheduled(
        fixedDelayString =
            "${payflow.outbox.polling.fixed-delay}",
        initialDelayString =
            "${payflow.outbox.polling.initial-delay}"
    )
    public void publishAvailableEvents() {
        try {
            PublishOutboxEventsResult result =
                metrics.record(() ->
                    publishOutboxEventsUseCase
                        .publishAvailable(command)
                );

            logResult(result);
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Outbox polling cycle failed. "
                    + "publisherId={}",
                command.publisherId(),
                exception
            );
        } finally {
            refreshBacklogMetrics();
        }
    }

    private void refreshBacklogMetrics() {
        try {
            backlogMetrics.refresh();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "Outbox backlog metrics could not "
                    + "be refreshed. publisherId={}",
                command.publisherId(),
                exception
            );
        }
    }

    private static void logResult(
        PublishOutboxEventsResult result
    ) {
        if (result.claimedCount() == 0) {
            LOGGER.debug(
                "No available outbox events were found."
            );

            return;
        }

        LOGGER.info(
            "Outbox polling cycle completed. "
                + "claimed={}, published={}, "
                + "retried={}, failed={}, "
                + "unresolved={}",
            result.claimedCount(),
            result.publishedCount(),
            result.retriedCount(),
            result.failedCount(),
            result.unresolvedCount()
        );
    }
}
