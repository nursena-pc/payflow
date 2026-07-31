package com.nursena.payflow.clientcontext.adapter.in.web;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.nursena.payflow.clientcontext.domain.ClientAddressResolutionOutcome;
import com.nursena.payflow.clientcontext.domain.ClientAddressSource;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

final class ClientAddressResolutionMetrics
    implements ClientAddressResolutionObserver {

    static final String METRIC_NAME =
        "payflow.security.client_context.decisions";

    private final Map<
        ClientAddressSource,
        Map<
            ClientAddressResolutionOutcome,
            Counter
        >
    > counters;

    ClientAddressResolutionMetrics(
        MeterRegistry meterRegistry
    ) {
        Objects.requireNonNull(
            meterRegistry,
            "meter registry must not be null"
        );

        this.counters =
            createCounters(meterRegistry);
    }

    @Override
    public void record(
        ClientAddressSource source,
        ClientAddressResolutionOutcome outcome
    ) {
        Objects.requireNonNull(
            source,
            "client address source must not be null"
        );

        Objects.requireNonNull(
            outcome,
            "client address outcome must not be null"
        );

        counters
            .get(source)
            .get(outcome)
            .increment();
    }

    private static Map<
        ClientAddressSource,
        Map<
            ClientAddressResolutionOutcome,
            Counter
        >
    > createCounters(
        MeterRegistry meterRegistry
    ) {
        EnumMap<
            ClientAddressSource,
            Map<
                ClientAddressResolutionOutcome,
                Counter
            >
        > result =
            new EnumMap<>(
                ClientAddressSource.class
            );

        for (
            ClientAddressSource source
                : ClientAddressSource.values()
        ) {
            EnumMap<
                ClientAddressResolutionOutcome,
                Counter
            > outcomeCounters =
                new EnumMap<>(
                    ClientAddressResolutionOutcome.class
                );

            for (
                ClientAddressResolutionOutcome outcome
                    : ClientAddressResolutionOutcome
                        .values()
            ) {
                Counter counter =
                    Counter
                        .builder(METRIC_NAME)
                        .description(
                            "Effective client-address "
                                + "resolution decisions"
                        )
                        .tag(
                            "source",
                            tagValue(source)
                        )
                        .tag(
                            "outcome",
                            tagValue(outcome)
                        )
                        .register(meterRegistry);

                outcomeCounters.put(
                    outcome,
                    counter
                );
            }

            result.put(
                source,
                Map.copyOf(outcomeCounters)
            );
        }

        return Map.copyOf(result);
    }

    private static String tagValue(
        Enum<?> value
    ) {
        return value
            .name()
            .toLowerCase(Locale.ROOT);
    }
}
