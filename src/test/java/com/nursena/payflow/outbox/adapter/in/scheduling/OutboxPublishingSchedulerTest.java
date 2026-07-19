package com.nursena.payflow.outbox.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsCommand;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsResult;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;

@ExtendWith(MockitoExtension.class)
class OutboxPublishingSchedulerTest {

    @Mock
    private PublishOutboxEventsUseCase useCase;

    private OutboxPublishingScheduler scheduler;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry =
            new SimpleMeterRegistry();

        scheduler =
            new OutboxPublishingScheduler(
                useCase,
                new OutboxPollingMetrics(
                    meterRegistry
                ),
                new OutboxPollingProperties(
                    true,
                    "publisher-1",
                    100,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(5)
                )
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
    }
}
