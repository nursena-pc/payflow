package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015PerformanceHarnessContractTest {

    private static final Path PERFORMANCE_CONTRACT =
        Path.of(
            "docs",
            "performance",
            "abuse-protection-performance-contract.md"
        );

    private static final Path K6_COMPOSE =
        Path.of("performance", "k6", "compose.yml");

    private static final Path K6_RUNNER =
        Path.of("performance", "k6", "run.ps1");

    private static final Path K6_README =
        Path.of("performance", "k6", "README.md");

    private static final Path K6_SMOKE =
        Path.of(
            "performance",
            "k6",
            "scenarios",
            "harness-smoke.js"
        );

    @Test
    void shouldPinTheExternalLoadGenerator() throws IOException {
        String compose = Files.readString(K6_COMPOSE);

        assertThat(compose)
            .contains("image: grafana/k6:2.1.0")
            .doesNotContain("grafana/k6:latest");
    }

    @Test
    void shouldIsolateTheLoadStackFromDeveloperHostPorts()
        throws IOException {

        String compose = Files.readString(K6_COMPOSE);
        String readme = Files.readString(K6_README);

        long resetPortDeclarations = compose
            .lines()
            .filter(line -> line.trim().equals("ports: !reset []"))
            .count();

        assertThat(resetPortDeclarations).isEqualTo(4);

        assertThat(compose)
            .contains(
                "ports: !override",
                "PAYFLOW_PERFORMANCE_APP_PORT:-18080",
                "K6_BASE_URL:-http://app:8080"
            );

        assertThat(readme)
            .contains(
                "isolated from an ordinary developer Compose stack",
                "PostgreSQL, Redis, Kafka, and Mailpit",
                "host port `18080`",
                "`http://app:8080`"
            );
    }

    @Test
    void shouldKeepLoadExecutionOutsideMavenLifecycle()
        throws IOException {

        String runner = Files.readString(K6_RUNNER);
        String readme = Files.readString(K6_README);
        String contract = Files.readString(PERFORMANCE_CONTRACT);
        String normalizedContract = contract.replaceAll("\\s+", " ");

        assertThat(runner)
            .contains(
                "'-p', $ProjectName",
                "--profile', 'loadtest'",
                "docker compose @ComposeArguments",
                "Assert-NativeSuccess",
                "GRAFANA_ADMIN_PASSWORD",
                "payflow-performance-compose-validation-only"
            );

        assertThat(readme)
            .contains(
                "$env:GRAFANA_ADMIN_PASSWORD",
                "local-only placeholder",
                "monitoring profile is not started"
            );

        assertThat(normalizedContract)
            .contains(
                "outside Maven's `test` and `verify` lifecycles",
                "No load result produced by this checkpoint is accepted"
            );
    }

    @Test
    void shouldFreezeWorkstationBudgetsBeforeEvidence()
        throws IOException {

        assertThat(Files.readString(PERFORMANCE_CONTRACT))
            .contains(
                "`p(95)` request duration must be at most 750 ms",
                "`p(99)` request duration must be at most 1500 ms",
                "unexpected HTTP/transport failure rate must be below 0.5%",
                "`dropped_iterations` must be zero",
                "within 30 seconds",
                "`ACTIVATE`",
                "`DEFER`",
                "no more than 10%"
            );
    }

    @Test
    void shouldKeepCheckpointOneAsConnectivityOnly()
        throws IOException {

        String smoke = Files.readString(K6_SMOKE);

        assertThat(smoke)
            .contains(
                "executor: 'shared-iterations'",
                "vus: 1",
                "iterations: 1",
                "/api/v1/system/health"
            )
            .doesNotContain(
                "/api/v1/auth/register",
                "password",
                "Authorization"
            );
    }

    @Test
    void shouldKeepRawPerformanceResultsOutOfGit()
        throws IOException {

        String gitignore = Files.readString(Path.of(".gitignore"));
        String contract = Files.readString(PERFORMANCE_CONTRACT);

        assertThat(gitignore)
            .contains("performance/results/");

        assertThat(contract)
            .contains(
                "performance/results/",
                "ignored by Git",
                "Runtime-generated test identities and credentials must be disposable"
            );
    }
}
