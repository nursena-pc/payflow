
package com.nursena.payflow.user.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import java.time.Duration;

import com.nursena.payflow.user.application.port.out
    .LoginRateLimitDecision;
import com.nursena.payflow.user.application.port.out
    .LoginRateLimitDimension;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple
    .SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginRateLimitMetricsTest {

    private SimpleMeterRegistry meterRegistry;

    private LoginRateLimitMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry =
            new SimpleMeterRegistry();

        metrics =
            new LoginRateLimitMetrics(
                meterRegistry
            );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void shouldRecordAllowedAndBlockedDecisions() {
        metrics.recordDecision(
            LoginRateLimitDecision.allowed()
        );

        metrics.recordDecision(
            LoginRateLimitDecision.blocked(
                LoginRateLimitDimension.BOTH,
                Duration.ofSeconds(30)
            )
        );

        assertCounter(
            LoginRateLimitMetrics.DECISIONS_METRIC,
            "outcome",
            "allowed",
            "dimension",
            "none",
            1.0
        );

        assertCounter(
            LoginRateLimitMetrics.DECISIONS_METRIC,
            "outcome",
            "blocked",
            "dimension",
            "both",
            1.0
        );
    }

    @Test
    void shouldRecordBoundedRedisFailureOperations() {
        metrics.recordRedisFailure(
            "evaluate"
        );

        metrics.recordRedisFailure(
            "reset"
        );

        assertCounter(
            LoginRateLimitMetrics
                .REDIS_FAILURES_METRIC,
            "operation",
            "evaluate",
            1.0
        );

        assertCounter(
            LoginRateLimitMetrics
                .REDIS_FAILURES_METRIC,
            "operation",
            "reset",
            1.0
        );
    }

    @Test
    void shouldRejectUnexpectedOperationTag() {
        assertThatThrownBy(() ->
            metrics.recordRedisFailure(
                "email:nursena@example.com"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "operation must be evaluate or reset"
            );
    }

    private void assertCounter(
        String metricName,
        String firstTagName,
        String firstTagValue,
        String secondTagName,
        String secondTagValue,
        double expectedCount
    ) {
        Counter counter =
            meterRegistry
                .get(metricName)
                .tag(
                    firstTagName,
                    firstTagValue
                )
                .tag(
                    secondTagName,
                    secondTagValue
                )
                .counter();

        assertThat(counter.count())
            .isEqualTo(expectedCount);
    }

    private void assertCounter(
        String metricName,
        String tagName,
        String tagValue,
        double expectedCount
    ) {
        Counter counter =
            meterRegistry
                .get(metricName)
                .tag(
                    tagName,
                    tagValue
                )
                .counter();

        assertThat(counter.count())
            .isEqualTo(expectedCount);
    }
}
