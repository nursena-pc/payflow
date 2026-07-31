package com.nursena.payflow.clientcontext.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Collectors;

import com.nursena.payflow.clientcontext.domain.ClientAddressResolutionOutcome;
import com.nursena.payflow.clientcontext.domain.ClientAddressSource;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

class ClientAddressResolutionMetricsTest {

    @Test
    void shouldPreRegisterBoundedSourceAndOutcomeSeries() {
        SimpleMeterRegistry meterRegistry =
            new SimpleMeterRegistry();

        new ClientAddressResolutionMetrics(
            meterRegistry
        );

        assertThat(
            meterRegistry
                .find(
                    ClientAddressResolutionMetrics
                        .METRIC_NAME
                )
                .meters()
        )
            .hasSize(
                ClientAddressSource.values().length
                    * ClientAddressResolutionOutcome
                        .values().length
            )
            .allSatisfy(
                meter -> {
                    assertThat(
                        meter
                            .getId()
                            .getTags()
                    )
                        .extracting(Tag::getKey)
                        .containsExactlyInAnyOrder(
                            "source",
                            "outcome"
                        );
                }
            );
    }

    @Test
    void shouldIncrementOnlyEnumDerivedSeries() {
        SimpleMeterRegistry meterRegistry =
            new SimpleMeterRegistry();

        ClientAddressResolutionMetrics metrics =
            new ClientAddressResolutionMetrics(
                meterRegistry
            );

        metrics.record(
            ClientAddressSource.FORWARDED,
            ClientAddressResolutionOutcome.RESOLVED
        );

        metrics.record(
            ClientAddressSource.FORWARDED,
            ClientAddressResolutionOutcome.RESOLVED
        );

        Counter counter =
            meterRegistry
                .find(
                    ClientAddressResolutionMetrics
                        .METRIC_NAME
                )
                .tags(
                    "source",
                    "forwarded",
                    "outcome",
                    "resolved"
                )
                .counter();

        assertThat(counter)
            .isNotNull();

        assertThat(counter.count())
            .isEqualTo(2.0);

        Set<String> meterIdentifiers =
            meterRegistry
                .getMeters()
                .stream()
                .map(Meter::getId)
                .map(Object::toString)
                .collect(
                    Collectors.toUnmodifiableSet()
                );

        assertThat(meterIdentifiers)
            .noneMatch(
                identifier ->
                    identifier.contains(
                        "203.0.113.9"
                    )
            )
            .noneMatch(
                identifier ->
                    identifier.contains(
                        "2001:db8::5"
                    )
            );
    }

    @Test
    void shouldRejectNullDimensions() {
        ClientAddressResolutionMetrics metrics =
            new ClientAddressResolutionMetrics(
                new SimpleMeterRegistry()
            );

        assertThatThrownBy(
            () -> metrics.record(
                null,
                ClientAddressResolutionOutcome.RESOLVED
            )
        )
            .isInstanceOf(
                NullPointerException.class
            );

        assertThatThrownBy(
            () -> metrics.record(
                ClientAddressSource.FORWARDED,
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            );
    }
}
