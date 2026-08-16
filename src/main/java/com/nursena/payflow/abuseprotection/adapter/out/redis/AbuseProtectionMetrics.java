package com.nursena.payflow.abuseprotection.adapter.out.redis;

import java.util.Locale;
import java.util.Objects;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

final class AbuseProtectionMetrics {

    static final String DECISIONS_METRIC =
        "payflow.security.abuse_protection.decisions";

    static final String REDIS_FAILURES_METRIC =
        "payflow.security.abuse_protection.redis.failures";

    private static final String WORKFLOW_TAG =
        "workflow";

    private static final String OUTCOME_TAG =
        "outcome";

    private static final String REASON_TAG =
        "reason";

    private static final String FAILURE_MODE_TAG =
        "failure_mode";

    private final MeterRegistry meterRegistry;

    AbuseProtectionMetrics(
        MeterRegistry meterRegistry
    ) {
        this.meterRegistry =
            Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
            );
    }

    void recordDecision(
        AbuseProtectionWorkflow workflow,
        AbuseProtectionDecision decision
    ) {
        AbuseProtectionDecision validatedDecision =
            Objects.requireNonNull(
                decision,
                "decision must not be null"
            );

        recordDecision(
            workflow,
            validatedDecision.isAllowed()
                ? "allowed"
                : "blocked",
            validatedDecision.isAllowed()
                ? "none"
                : dimensionName(validatedDecision)
        );
    }

    void recordDisabled(
        AbuseProtectionWorkflow workflow
    ) {
        recordDecision(
            workflow,
            "disabled",
            "none"
        );
    }

    void recordDependencyBypass(
        AbuseProtectionWorkflow workflow
    ) {
        recordDecision(
            workflow,
            "dependency_bypass",
            "dependency_failure"
        );
    }

    void recordRedisFailure(
        AbuseProtectionWorkflow workflow,
        AbuseProtectionFailureMode failureMode
    ) {
        Counter.builder(
                REDIS_FAILURES_METRIC
            )
            .description(
                "Number of Redis dependency failures while "
                    + "enforcing generalized abuse protection."
            )
            .baseUnit("failures")
            .tags(
                WORKFLOW_TAG,
                workflowName(workflow),
                FAILURE_MODE_TAG,
                failureModeName(failureMode)
            )
            .register(meterRegistry)
            .increment();
    }

    private void recordDecision(
        AbuseProtectionWorkflow workflow,
        String outcome,
        String reason
    ) {
        Counter.builder(
                DECISIONS_METRIC
            )
            .description(
                "Number of generalized abuse-protection decisions."
            )
            .baseUnit("decisions")
            .tags(
                WORKFLOW_TAG,
                workflowName(workflow),
                OUTCOME_TAG,
                validateOutcome(outcome),
                REASON_TAG,
                validateReason(reason)
            )
            .register(meterRegistry)
            .increment();
    }

    private static String workflowName(
        AbuseProtectionWorkflow workflow
    ) {
        return Objects.requireNonNull(
            workflow,
            "workflow must not be null"
        ).configurationKey();
    }

    private static String dimensionName(
        AbuseProtectionDecision decision
    ) {
        return decision
            .blockedDimension()
            .name()
            .toLowerCase(Locale.ROOT);
    }

    private static String failureModeName(
        AbuseProtectionFailureMode failureMode
    ) {
        return Objects.requireNonNull(
            failureMode,
            "failureMode must not be null"
        )
            .name()
            .toLowerCase(Locale.ROOT);
    }

    private static String validateOutcome(
        String value
    ) {
        if (
            !"allowed".equals(value)
                && !"blocked".equals(value)
                && !"disabled".equals(value)
                && !"dependency_bypass".equals(value)
        ) {
            throw new IllegalArgumentException(
                "unsupported abuse-protection outcome"
            );
        }

        return value;
    }

    private static String validateReason(
        String value
    ) {
        if (
            !"none".equals(value)
                && !"identity".equals(value)
                && !"client".equals(value)
                && !"both".equals(value)
                && !"dependency_failure".equals(value)
        ) {
            throw new IllegalArgumentException(
                "unsupported abuse-protection reason"
            );
        }

        return value;
    }
}
