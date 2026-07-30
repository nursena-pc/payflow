
package com.nursena.payflow.user.adapter.out.ratelimit;

import java.util.Locale;
import java.util.Objects;

import com.nursena.payflow.user.application.port.out
    .LoginRateLimitDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

final class LoginRateLimitMetrics {

    static final String DECISIONS_METRIC =
        "payflow.auth.login.rate_limit.decisions";

    static final String REDIS_FAILURES_METRIC =
        "payflow.auth.login.rate_limit.redis.failures";

    private static final String OUTCOME_TAG =
        "outcome";

    private static final String DIMENSION_TAG =
        "dimension";

    private static final String OPERATION_TAG =
        "operation";

    private final MeterRegistry meterRegistry;

    LoginRateLimitMetrics(
        MeterRegistry meterRegistry
    ) {
        this.meterRegistry =
            Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
            );
    }

    void recordDecision(
        LoginRateLimitDecision decision
    ) {
        LoginRateLimitDecision validatedDecision =
            Objects.requireNonNull(
                decision,
                "decision must not be null"
            );

        Counter.builder(
                DECISIONS_METRIC
            )
            .description(
                "Number of Redis-backed login "
                    + "rate-limit decisions."
            )
            .baseUnit("decisions")
            .tags(
                OUTCOME_TAG,
                validatedDecision.isAllowed()
                    ? "allowed"
                    : "blocked",
                DIMENSION_TAG,
                dimensionName(
                    validatedDecision
                )
            )
            .register(
                meterRegistry
            )
            .increment();
    }

    void recordRedisFailure(
        String operation
    ) {
        Counter.builder(
                REDIS_FAILURES_METRIC
            )
            .description(
                "Number of Redis failures while "
                    + "enforcing login rate limits."
            )
            .baseUnit("failures")
            .tag(
                OPERATION_TAG,
                validateOperation(operation)
            )
            .register(
                meterRegistry
            )
            .increment();
    }

    private static String dimensionName(
        LoginRateLimitDecision decision
    ) {
        return decision
            .blockedDimension()
            .name()
            .toLowerCase(
                Locale.ROOT
            );
    }

    private static String validateOperation(
        String value
    ) {
        if (
            !"evaluate".equals(value)
                && !"reset".equals(value)
        ) {
            throw new IllegalArgumentException(
                "operation must be evaluate or reset"
            );
        }

        return value;
    }
}
