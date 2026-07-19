package com.nursena.payflow.outbox.adapter.in.scheduling;

import java.util.Objects;
import java.util.function.Supplier;

import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

final class OutboxPollingMetrics {

    static final String POLLING_DURATION_METRIC =
        "payflow.outbox.polling.duration";

    static final String EVENTS_METRIC =
        "payflow.outbox.events";

    private static final String OUTCOME_TAG =
        "outcome";

    private final MeterRegistry meterRegistry;

    private final Timer successfulCycleTimer;
    private final Timer failedCycleTimer;

    private final Counter claimedEvents;
    private final Counter publishedEvents;
    private final Counter retriedEvents;
    private final Counter failedEvents;
    private final Counter unresolvedEvents;

    OutboxPollingMetrics(
        MeterRegistry meterRegistry
    ) {
        this.meterRegistry =
            Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
            );

        successfulCycleTimer =
            pollingTimer(
                meterRegistry,
                "success"
            );

        failedCycleTimer =
            pollingTimer(
                meterRegistry,
                "failure"
            );

        claimedEvents =
            eventCounter(
                meterRegistry,
                "claimed"
            );

        publishedEvents =
            eventCounter(
                meterRegistry,
                "published"
            );

        retriedEvents =
            eventCounter(
                meterRegistry,
                "retried"
            );

        failedEvents =
            eventCounter(
                meterRegistry,
                "failed"
            );

        unresolvedEvents =
            eventCounter(
                meterRegistry,
                "unresolved"
            );
    }

    PublishOutboxEventsResult record(
        Supplier<PublishOutboxEventsResult>
            pollingCycle
    ) {
        Objects.requireNonNull(
            pollingCycle,
            "pollingCycle must not be null"
        );

        Timer.Sample sample =
            Timer.start(meterRegistry);

        try {
            PublishOutboxEventsResult result =
                Objects.requireNonNull(
                    pollingCycle.get(),
                    "pollingCycle must not return null"
                );

            recordEvents(result);

            sample.stop(
                successfulCycleTimer
            );

            return result;
        } catch (RuntimeException exception) {
            sample.stop(
                failedCycleTimer
            );

            throw exception;
        }
    }

    private void recordEvents(
        PublishOutboxEventsResult result
    ) {
        increment(
            claimedEvents,
            result.claimedCount()
        );

        increment(
            publishedEvents,
            result.publishedCount()
        );

        increment(
            retriedEvents,
            result.retriedCount()
        );

        increment(
            failedEvents,
            result.failedCount()
        );

        increment(
            unresolvedEvents,
            result.unresolvedCount()
        );
    }

    private static Timer pollingTimer(
        MeterRegistry meterRegistry,
        String outcome
    ) {
        return Timer.builder(
                POLLING_DURATION_METRIC
            )
            .description(
                "Duration of transactional "
                    + "outbox polling cycles."
            )
            .tag(
                OUTCOME_TAG,
                outcome
            )
            .register(
                meterRegistry
            );
    }

    private static Counter eventCounter(
        MeterRegistry meterRegistry,
        String outcome
    ) {
        return Counter.builder(
                EVENTS_METRIC
            )
            .description(
                "Number of transactional "
                    + "outbox event outcomes."
            )
            .baseUnit("events")
            .tag(
                OUTCOME_TAG,
                outcome
            )
            .register(
                meterRegistry
            );
    }

    private static void increment(
        Counter counter,
        int amount
    ) {
        if (amount > 0) {
            counter.increment(amount);
        }
    }
}
