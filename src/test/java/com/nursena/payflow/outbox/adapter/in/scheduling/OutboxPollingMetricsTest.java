package com.nursena.payflow.outbox.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxPollingMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private OutboxPollingMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry =
            new SimpleMeterRegistry();

        metrics =
            new OutboxPollingMetrics(
                meterRegistry
            );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void shouldRecordSuccessfulPollingCycleAndEventOutcomes() {
        PublishOutboxEventsResult expected =
            new PublishOutboxEventsResult(
                5,
                2,
                1,
                1,
                1
            );

        PublishOutboxEventsResult actual =
            metrics.record(() -> expected);

        assertThat(actual)
            .isSameAs(expected);

        assertTimerCount(
            "success",
            1
        );

        assertTimerCount(
            "failure",
            0
        );

        assertEventCount(
            "claimed",
            5
        );

        assertEventCount(
            "published",
            2
        );

        assertEventCount(
            "retried",
            1
        );

        assertEventCount(
            "failed",
            1
        );

        assertEventCount(
            "unresolved",
            1
        );
    }

    @Test
    void shouldRecordFailedPollingCycleAndRethrowFailure() {
        IllegalStateException failure =
            new IllegalStateException(
                "Database is unavailable."
            );

        assertThatThrownBy(() ->
            metrics.record(() -> {
                throw failure;
            })
        )
            .isSameAs(failure);

        assertTimerCount(
            "success",
            0
        );

        assertTimerCount(
            "failure",
            1
        );

        assertEventCount(
            "claimed",
            0
        );

        assertEventCount(
            "published",
            0
        );

        assertEventCount(
            "retried",
            0
        );

        assertEventCount(
            "failed",
            0
        );

        assertEventCount(
            "unresolved",
            0
        );
    }

    @Test
    void shouldNotIncrementEventCountersForEmptyCycle() {
        metrics.record(
            PublishOutboxEventsResult::empty
        );

        assertTimerCount(
            "success",
            1
        );

        assertEventCount(
            "claimed",
            0
        );

        assertEventCount(
            "published",
            0
        );
    }

    private void assertTimerCount(
        String outcome,
        long expected
    ) {
        assertThat(
            meterRegistry.get(
                    OutboxPollingMetrics
                        .POLLING_DURATION_METRIC
                )
                .tag(
                    "outcome",
                    outcome
                )
                .timer()
                .count()
        ).isEqualTo(expected);
    }

    private void assertEventCount(
        String outcome,
        double expected
    ) {
        assertThat(
            meterRegistry.get(
                    OutboxPollingMetrics
                        .EVENTS_METRIC
                )
                .tag(
                    "outcome",
                    outcome
                )
                .counter()
                .count()
        ).isEqualTo(expected);
    }
}
