package com.nursena.payflow.outbox.adapter.in.scheduling;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.nursena.payflow.outbox.application.model.OutboxBacklogSnapshot;
import com.nursena.payflow.outbox.application.port.out.OutboxBacklogQueryPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

final class OutboxBacklogMetrics {

    static final String BACKLOG_SIZE_METRIC =
        "payflow.outbox.backlog.size";

    static final String OLDEST_EVENT_AGE_METRIC =
        "payflow.outbox.backlog.oldest.age";

    private final OutboxBacklogQueryPort queryPort;
    private final Clock clock;

    private final AtomicReference<
        OutboxBacklogSnapshot
        > snapshot =
        new AtomicReference<>(
            OutboxBacklogSnapshot.empty()
        );

    OutboxBacklogMetrics(
        OutboxBacklogQueryPort queryPort,
        MeterRegistry meterRegistry,
        Clock clock
    ) {
        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "queryPort must not be null"
            );

        Objects.requireNonNull(
            meterRegistry,
            "meterRegistry must not be null"
        );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        Gauge.builder(
                BACKLOG_SIZE_METRIC,
                snapshot,
                state ->
                    state.get()
                        .eventCount()
            )
            .description(
                "Number of active transactional "
                    + "outbox events."
            )
            .register(
                meterRegistry
            );

        Gauge.builder(
                OLDEST_EVENT_AGE_METRIC,
                snapshot,
                state ->
                    oldestEventAgeSeconds(
                        state.get()
                    )
            )
            .description(
                "Age of the oldest active "
                    + "transactional outbox event."
            )
            .baseUnit("seconds")
            .register(
                meterRegistry
            );
    }

    void refresh() {
        OutboxBacklogSnapshot loadedSnapshot =
            Objects.requireNonNull(
                queryPort.loadSnapshot(),
                "queryPort must not return null"
            );

        snapshot.set(
            loadedSnapshot
        );
    }

    private double oldestEventAgeSeconds(
        OutboxBacklogSnapshot currentSnapshot
    ) {
        return currentSnapshot
            .oldestCreatedAt()
            .map(
                this::ageInSeconds
            )
            .orElse(0.0);
    }

    private double ageInSeconds(
        Instant oldestCreatedAt
    ) {
        Duration age =
            Duration.between(
                oldestCreatedAt,
                clock.instant()
            );

        if (age.isNegative()) {
            return 0.0;
        }

        return age.toMillis()
            / 1000.0;
    }
}
