package com.nursena.payflow.abuseprotection.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AbuseProtectionOperationsDocumentationContractTest {

    private static final Path MONITORING =
        Path.of("docs", "monitoring.md");

    private static final Path ALERTING =
        Path.of("docs", "alerting.md");

    private static final Path POLICY_GUIDE =
        Path.of("docs", "abuse-protection.md");

    private static final Path RUNBOOK =
        Path.of(
            "docs",
            "operations",
            "abuse-protection-observability.md"
        );

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    @Test
    void shouldDescribeTheProvisionedMonitoringAndNotificationStack()
        throws IOException {

        String monitoring = Files.readString(MONITORING);
        String alerting = Files.readString(ALERTING);

        assertThat(monitoring)
            .contains(
                "Prometheus",
                "Alertmanager",
                "Grafana",
                "Mailpit",
                "abuse-protection.json",
                "abuse-protection-alerts.yml"
            )
            .doesNotContain(
                "Alertmanager is not currently part of the PayFlow stack"
            );

        assertThat(alerting)
            .contains(
                "alertmanager:9093",
                "critical-email",
                "warning-email",
                "PayFlowAbuseProtectionRedisFailures",
                "PayFlowAbuseProtectionBlockingPressure",
                "PayFlowAbuseProtectionDependencyBypass"
            );

        int fenceCount =
            alerting.split("```", -1).length - 1;

        assertThat(fenceCount % 2).isZero();
    }

    @Test
    void shouldDocumentActionableThresholdsAndSafeResponse()
        throws IOException {

        String runbook = Files.readString(RUNBOOK);

        assertThat(runbook)
            .contains(
                "PayFlowAbuseProtectionRedisFailures",
                "PayFlowAbuseProtectionBlockingPressure",
                "PayFlowAbuseProtectionDependencyBypass",
                "five-minute window",
                "at least 25 blocked decisions",
                "FAIL_CLOSED",
                "FAIL_OPEN",
                "ABUSE_PROTECTION_ENABLED",
                "Do not switch the affected workflow to `FAIL_OPEN`",
                "Do not silently disable generalized protection",
                "Do not clear individual Redis quota keys"
            );
    }

    @Test
    void shouldKeepSensitiveMaterialOutOfTheOperationalWorkflow()
        throws IOException {

        String policyGuide = Files.readString(POLICY_GUIDE);
        String runbook = Files.readString(RUNBOOK);

        assertThat(policyGuide)
            .contains(
                "bounded `workflow`",
                "`outcome`",
                "`reason`",
                "`failure_mode`",
                "prohibited from metric labels"
            );

        assertThat(runbook)
            .contains(
                "Never copy the following into dashboards",
                "email addresses",
                "user UUIDs",
                "raw client addresses",
                "Redis keys, counters, or TTL values",
                "request URIs",
                "raw exception classes",
                "Safe incident evidence includes alert name"
            )
            .doesNotContain(
                "redis-cli DEL",
                "KEYS abuse",
                "FLUSHALL",
                "disable generalized protection to recover"
            );
    }

    @Test
    void shouldMarkIncrementFiveDeliveredInTheRoadmap()
        throws IOException {

        String roadmap = Files.readString(ROADMAP);

        assertThat(roadmap)
            .contains(
                "- [x] Expose bounded decision and Redis-failure metrics",
                "- [x] Provision Grafana dashboards for quota outcomes",
                "- [x] Provision actionable alert rules",
                "- [x] Document investigation, safe mitigation, rollback",
                "- [x] Verify logs, metrics, traces, dashboards, and alerts"
            );
    }
}
