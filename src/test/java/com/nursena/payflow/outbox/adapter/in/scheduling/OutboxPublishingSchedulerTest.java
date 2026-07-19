package com.nursena.payflow.outbox.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nursena.payflow.outbox.application.model.OutboxBacklogSnapshot;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsCommand;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsResult;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsUseCase;
import com.nursena.payflow.outbox.application.port.out.OutboxBacklogQueryPort;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class OutboxPublishingSchedulerTest {

    @Mock
    private PublishOutboxEventsUseCase useCase;

    @Mock
    private OutboxBacklogQueryPort backlogQueryPort;

    private OutboxPublishingScheduler scheduler;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry =
            new SimpleMeterRegistry();

        when(backlogQueryPort.loadSnapshot())
            .thenReturn(
                OutboxBacklogSnapshot.empty()
            );

        OutboxPollingProperties properties =
            new OutboxPollingProperties(
                true,
                "publisher-1",
                100,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
            );

        OutboxBacklogMetrics backlogMetrics =
            new OutboxBacklogMetrics(
                backlogQueryPort,
                meterRegistry,
                Clock.fixed(
                    Instant.parse(
                        "2026-07-19T12:00:00Z"
                    ),
                    ZoneOffset.UTC
                )
            );

        scheduler =
            new OutboxPublishingScheduler(
                useCase,
                new OutboxPollingMetrics(
                    meterRegistry
                ),
                backlogMetrics,
                properties
            );
    }

    @Test
    void shouldPublishAvailableEventsUsingConfiguredCommand() {
        PublishOutboxEventsCommand command =
            new PublishOutboxEventsCommand(
                "publisher-1",
                100,
                Duration.ofSeconds(30)
            );

        when(useCase.publishAvailable(command))
            .thenReturn(
                new PublishOutboxEventsResult(
                    3,
                    2,
                    1,
                    0,
                    0
                )
            );

        scheduler.publishAvailableEvents();

        verify(useCase)
            .publishAvailable(command);

        verify(backlogQueryPort)
            .loadSnapshot();
    }

    @Test
    void shouldContainUnexpectedPollingFailure() {
        PublishOutboxEventsCommand command =
            new PublishOutboxEventsCommand(
                "publisher-1",
                100,
                Duration.ofSeconds(30)
            );

        when(useCase.publishAvailable(command))
            .thenThrow(
                new IllegalStateException(
                    "Database is unavailable."
                )
            );

        assertThatCode(
            scheduler::publishAvailableEvents
        ).doesNotThrowAnyException();

        verify(useCase)
            .publishAvailable(command);

        verify(backlogQueryPort)
            .loadSnapshot();
    }
}
