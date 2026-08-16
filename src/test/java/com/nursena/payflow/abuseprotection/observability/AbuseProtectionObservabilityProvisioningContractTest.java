package com.nursena.payflow.abuseprotection.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AbuseProtectionObservabilityProvisioningContractTest {

    private static final Path DASHBOARD =
        Path.of(
            "observability",
            "grafana",
            "dashboards",
            "abuse-protection.json"
        );

    private static final Path DASHBOARD_PROVISIONING =
        Path.of(
            "observability",
            "grafana",
            "provisioning",
            "dashboards",
            "dashboards.yml"
        );

    private static final Path ALERT_RULES =
        Path.of(
            "observability",
            "prometheus",
            "rules",
            "abuse-protection-alerts.yml"
        );

    private static final Path PROMETHEUS_CONFIGURATION =
        Path.of(
            "observability",
            "prometheus",
            "prometheus.yml"
        );

    private static final String DECISIONS_METRIC =
        "payflow_security_abuse_protection_decisions_total";

    private static final String REDIS_FAILURES_METRIC =
        "payflow_security_abuse_protection_redis_failures_total";

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldProvisionDedicatedBoundedSecurityDashboard()
        throws IOException {

        JsonNode dashboard = objectMapper.readTree(
            Files.readString(DASHBOARD)
        );

        String serialized = dashboard.toString();

        assertThat(dashboard.path("title").asText())
            .isEqualTo("PayFlow Abuse Protection");

        assertThat(dashboard.path("uid").asText())
            .isEqualTo("payflow-abuse-protection");

        assertThat(serialized)
            .contains(
                DECISIONS_METRIC,
                REDIS_FAILURES_METRIC,
                "sum by (workflow, outcome)",
                "sum by (workflow, reason)",
                "sum by (workflow, failure_mode)",
                "outcome=\\\"blocked\\\"",
                "outcome=~\\\"disabled|dependency_bypass\\\""
            )
            .doesNotContain(
                "email_address",
                "user_uuid",
                "jwt_subject",
                "challenge_token",
                "recovery_code",
                "step_up_grant",
                "client_address",
                "redis_key",
                "request_uri",
                "exception_class"
            );
    }

    @Test
    void shouldProvisionActionableBoundedAlertRules()
        throws IOException {

        String rules = Files.readString(ALERT_RULES);

        assertThat(rules)
            .contains(
                "name: payflow-abuse-protection",
                "alert: PayFlowAbuseProtectionRedisFailures",
                "alert: PayFlowAbuseProtectionBlockingPressure",
                "alert: PayFlowAbuseProtectionDependencyBypass",
                REDIS_FAILURES_METRIC,
                DECISIONS_METRIC,
                "sum by (workflow, failure_mode)",
                "sum by (workflow)",
                "component: abuse-protection",
                "severity: critical",
                "severity: warning",
                "for: 1m",
                "for: 5m",
                ">= 25"
            )
            .doesNotContain(
                "email_address",
                "user_uuid",
                "jwt_subject",
                "challenge_token",
                "recovery_code",
                "step_up_grant",
                "client_address",
                "redis_key",
                "request_uri",
                "exception_class"
            );
    }

    @Test
    void shouldLoadNewArtifactsThroughExistingDirectoryProvisioning()
        throws IOException {

        assertThat(
            Files.readString(PROMETHEUS_CONFIGURATION)
        )
            .contains(
                "/etc/prometheus/rules/*.yml"
            );

        assertThat(
            Files.readString(DASHBOARD_PROVISIONING)
        )
            .contains(
                "path: /var/lib/grafana/dashboards",
                "allowUiUpdates: false"
            );
    }
}
