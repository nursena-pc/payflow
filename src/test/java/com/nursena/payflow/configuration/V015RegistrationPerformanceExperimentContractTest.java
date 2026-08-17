package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015RegistrationPerformanceExperimentContractTest {

    private static final Path RUNNER = Path.of(
        "performance", "k6", "run.ps1"
    );
    private static final Path COMPOSE = Path.of(
        "performance", "k6", "compose.yml"
    );
    private static final Path WORKLOAD = Path.of(
        "performance", "k6", "lib", "workload.js"
    );
    private static final Path SCENARIO = Path.of(
        "performance", "k6", "scenarios",
        "registration-experiment.js"
    );
    private static final Path RECORDER = Path.of(
        "performance", "k6",
        "record-registration-experiment.ps1"
    );
    private static final Path METHOD = Path.of(
        "performance", "k6",
        "REGISTRATION_EXPERIMENT.md"
    );
    private static final Path REGISTER_CONTROLLER = Path.of(
        "src", "main", "java", "com", "nursena", "payflow",
        "user", "adapter", "in", "web",
        "RegisterUserController.java"
    );

    @Test
    void shouldKeepRegistrationLoadOutsideTheMavenLifecycle()
        throws IOException {

        String runner = Files.readString(RUNNER);
        String compose = Files.readString(COMPOSE);

        assertThat(runner)
            .contains(
                "'registration-experiment'",
                "/work/scenarios/registration-experiment.js"
            );

        assertThat(compose)
            .contains(
                "grafana/k6:2.1.0",
                "PAYFLOW_K6_REGISTRATION_RATE",
                "K6_REGISTRATION_PASSWORD"
            );
    }

    @Test
    void shouldExerciseTheExistingSuccessfulRegistrationContract()
        throws IOException {

        String scenario = Files.readString(SCENARIO);
        String controller = Files.readString(REGISTER_CONTROLLER);

        assertThat(scenario)
            .contains(
                "/api/v1/auth/register",
                "response.status === 201",
                "payflow_registration_created",
                "payflow_registration_request_duration",
                "payflow_registration_unexpected_failures"
            )
            .doesNotContain(
                "response.body",
                "console.log"
            );

        assertThat(controller)
            .contains(
                "@PostMapping(\"/register\")",
                "HttpStatus.CREATED",
                "responseCode = \"201\"",
                "responseCode = \"400\"",
                "responseCode = \"409\""
            );
    }

    @Test
    void shouldUseDisposableUniqueIdentitiesAndRuntimeOnlyPassword()
        throws IOException {

        String workload = Files.readString(WORKLOAD);
        String scenario = Files.readString(SCENARIO);

        assertThat(workload)
            .contains(
                "syntheticRegistrationEmail",
                "@example.invalid",
                "requiredRegistrationPassword",
                "K6_REGISTRATION_PASSWORD",
                "password.length < 12",
                "password.length > 72"
            )
            .doesNotContain(
                "StrongPassword123!",
                "PerformanceOnly123!"
            );

        assertThat(scenario)
            .doesNotContain(
                "@example.com",
                "password:"
            );
    }

    @Test
    void shouldFreezeTheBoundedUnprotectedExperimentBeforeMeasurement()
        throws IOException {

        String recorder = Files.readString(RECORDER);
        String method = Files.readString(METHOD);

        assertThat(recorder)
            .contains(
                "$RampRates = @(2, 4, 8, 16)",
                "-Name 'warmup'",
                "-DurationSeconds 10",
                "-Name 'baseline'",
                "-DurationSeconds 30",
                "$SaturationP95Ms = 1500.0",
                "$SaturationUnexpectedFailureRate = 0.01",
                "$RecoveryBudgetSeconds = 30",
                "registrationProtectionWired = $false",
                "decision = 'REVIEW_REQUIRED'"
            );

        assertThat(method)
            .contains(
                "10 seconds at 1 registration/second",
                "30 seconds at 1 registration/second",
                "2, 4, 8, and 16 registrations/second",
                "ACTIVATE",
                "DEFER",
                "p95 regression is no more than 10%"
            );
    }

    @Test
    void shouldRecordEnvironmentResourceAndRecoveryEvidence()
        throws IOException {

        String recorder = Files.readString(RECORDER);

        assertThat(recorder)
            .contains(
                "docker info",
                "docker stats",
                "java --version",
                "k6 version",
                "postPhaseAppCpuPercent",
                "postPhaseAppMemoryUsage",
                "recoveredWithinBudget",
                "Get-SummaryMetricValue",
                "$MetricsProperty.Value.PSObject.Properties[$MetricName]",
                "$MetricProperty.Value.PSObject.Properties['value']"
            );
    }

    @Test
    void shouldPreserveIsolationPrivacyAndExplicitReviewBoundary()
        throws IOException {

        String recorder = Files.readString(RECORDER);
        String method = Files.readString(METHOD);

        assertThat(recorder)
            .contains(
                "payflow-performance-registration",
                "down -v --remove-orphans",
                "performance\\results\\registration",
                "New-RegistrationPassword",
                "ABUSE_PROTECTION_ENABLED = 'true'",
                "Decision remains REVIEW_REQUIRED"
            )
            .doesNotContain(
                "docker compose down",
                "StrongPassword123!",
                "PerformanceOnly123!"
            );

        assertThat(method)
            .contains(
                "never chooses `ACTIVATE` or `DEFER`",
                "separate reviewed implementation checkpoint",
                "Raw experiment output remains under the"
            );
    }
}
