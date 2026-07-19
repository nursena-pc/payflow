package com.nursena.payflow.outbox.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.nursena.payflow.outbox.application.model.OutboxBacklogSnapshot;
import com.nursena.payflow.outbox.application.port.out.OutboxBacklogQueryPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxBacklogMetricsTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-07-19T12:00:00Z"
        );

    @Mock
    private OutboxBacklogQueryPort queryPort;

    private SimpleMeterRegistry meterRegistry;
    private OutboxBacklogMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry =
            new SimpleMeterRegistry();

        metrics =
            new OutboxBacklogMetrics(
                queryPort,
                meterRegistry,
                Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
                )
            );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void shouldExposeZeroValuesBeforeFirstRefresh() {
        assertBacklogSize(0.0);
        assertOldestAge(0.0);
    }

    @Test
    void shouldRefreshBacklogSizeAndOldestEventAge() {
        when(queryPort.loadSnapshot())
            .thenReturn(
                new OutboxBacklogSnapshot(
                    3,
                    Optional.of(
                        NOW.minusSeconds(45)
                    )
                )
            );

        metrics.refresh();

        assertBacklogSize(3.0);
        assertOldestAge(45.0);
    }

    @Test
    void shouldResetMetricsForEmptyBacklog() {
        when(queryPort.loadSnapshot())
            .thenReturn(
                new OutboxBacklogSnapshot(
                    2,
                    Optional.of(
                        NOW.minusSeconds(30)
                    )
                ),
                OutboxBacklogSnapshot.empty()
            );

        metrics.refresh();
        metrics.refresh();

        assertBacklogSize(0.0);
        assertOldestAge(0.0);
    }

    @Test
    void shouldPreserveLastSnapshotWhenRefreshFails() {
        when(queryPort.loadSnapshot())
            .thenReturn(
                new OutboxBacklogSnapshot(
                    2,
                    Optional.of(
                        NOW.minusSeconds(30)
                    )
                )
            )
            .thenThrow(
                new IllegalStateException(
                    "Database is unavailable."
                )
            );

        metrics.refresh();

        assertThatThrownBy(
            metrics::refresh
        )
            .isInstanceOf(
                IllegalStateException.class
            );

        assertBacklogSize(2.0);
        assertOldestAge(30.0);
    }

    private void assertBacklogSize(
        double expected
    ) {
        assertThat(
            meterRegistry.get(
                    OutboxBacklogMetrics
                        .BACKLOG_SIZE_METRIC
                )
                .gauge()
                .value()
        ).isEqualTo(expected);
    }

    private void assertOldestAge(
        double expected
    ) {
        assertThat(
            meterRegistry.get(
                    OutboxBacklogMetrics
                        .OLDEST_EVENT_AGE_METRIC
                )
                .gauge()
                .value()
        ).isEqualTo(expected);
    }
}
