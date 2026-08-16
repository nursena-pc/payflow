package com.nursena.payflow.abuseprotection.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDimension;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbuseProtectionMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private AbuseProtectionMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new AbuseProtectionMetrics(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void shouldRecordAllowedBlockedAndDisabledDecisions() {
        metrics.recordDecision(
            AbuseProtectionWorkflow.REGISTRATION,
            AbuseProtectionDecision.allowed()
        );

        metrics.recordDecision(
            AbuseProtectionWorkflow.EMAIL_VERIFICATION_REQUEST,
            AbuseProtectionDecision.blocked(
                AbuseProtectionDimension.BOTH,
                Duration.ofSeconds(30)
            )
        );

        metrics.recordDisabled(
            AbuseProtectionWorkflow.PASSWORD_RECOVERY_REQUEST
        );

        assertDecisionCounter(
            "registration",
            "allowed",
            "none",
            1.0
        );

        assertDecisionCounter(
            "email-verification-request",
            "blocked",
            "both",
            1.0
        );

        assertDecisionCounter(
            "password-recovery-request",
            "disabled",
            "none",
            1.0
        );
    }

    @Test
    void shouldRecordDependencyBypassAndFailureWithBoundedTags() {
        AbuseProtectionWorkflow workflow =
            AbuseProtectionWorkflow.STEP_UP_GRANT_ISSUANCE;

        metrics.recordRedisFailure(
            workflow,
            AbuseProtectionFailureMode.FAIL_OPEN
        );

        metrics.recordDependencyBypass(workflow);

        assertDecisionCounter(
            "step-up-grant-issuance",
            "dependency_bypass",
            "dependency_failure",
            1.0
        );

        Counter failureCounter = meterRegistry
            .get(AbuseProtectionMetrics.REDIS_FAILURES_METRIC)
            .tag("workflow", "step-up-grant-issuance")
            .tag("failure_mode", "fail_open")
            .counter();

        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldExposeOnlyClosedApplicationOwnedTagValues() {
        for (AbuseProtectionWorkflow workflow
            : AbuseProtectionWorkflow.values()) {
            metrics.recordDecision(
                workflow,
                AbuseProtectionDecision.allowed()
            );
        }

        assertThat(
            meterRegistry.find(
                AbuseProtectionMetrics.DECISIONS_METRIC
            ).counters()
        )
            .allSatisfy(counter -> {
                assertThat(counter.getId().getTag("workflow"))
                    .isIn(
                        "registration",
                        "email-verification-request",
                        "password-recovery-request",
                        "mfa-login-challenge-confirmation",
                        "step-up-grant-issuance"
                    );

                assertThat(counter.getId().getTag("outcome"))
                    .isIn(
                        "allowed",
                        "blocked",
                        "disabled",
                        "dependency_bypass"
                    );

                assertThat(counter.getId().getTag("reason"))
                    .isIn(
                        "none",
                        "identity",
                        "client",
                        "both",
                        "dependency_failure"
                    );
            });
    }

    private void assertDecisionCounter(
        String workflow,
        String outcome,
        String reason,
        double expectedCount
    ) {
        Counter counter = meterRegistry
            .get(AbuseProtectionMetrics.DECISIONS_METRIC)
            .tag("workflow", workflow)
            .tag("outcome", outcome)
            .tag("reason", reason)
            .counter();

        assertThat(counter.count()).isEqualTo(expectedCount);
    }
}
