package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015ProtectedWorkflowLoadScenarioContractTest {

    private static final Path K6_ROOT =
        Path.of("performance", "k6");

    @Test
    void shouldExposeOnlyTheReviewedProtectedWorkflowScenarios()
        throws IOException {

        String runner = Files.readString(K6_ROOT.resolve("run.ps1"));

        assertThat(runner)
            .contains(
                "'account-action-request'",
                "'mfa-challenge-confirm'",
                "'step-up-grant'",
                "/work/scenarios/account-action-request.js",
                "/work/scenarios/mfa-challenge-confirm.js",
                "/work/scenarios/step-up-grant.js"
            );
    }

    @Test
    void shouldKeepRepresentativeHttpContractsExact()
        throws IOException {

        String accountAction = Files.readString(
            K6_ROOT.resolve("scenarios/account-action-request.js")
        );
        String mfa = Files.readString(
            K6_ROOT.resolve("scenarios/mfa-challenge-confirm.js")
        );
        String stepUp = Files.readString(
            K6_ROOT.resolve("scenarios/step-up-grant.js")
        );

        String workload = Files.readString(
            K6_ROOT.resolve("lib/workload.js")
        );

        assertThat(accountAction)
            .contains(
                "/api/v1/auth/email-verification/requests",
                "response.status === 202",
                "operation: 'account_action_request'"
            );
        assertThat(workload).contains("@example.invalid");

        assertThat(mfa)
            .contains(
                "/api/v1/auth/mfa/challenges/confirm",
                "challengeToken: fixture.challengeToken",
                "code: fixture.code",
                "response.status === 200",
                "operation: 'mfa_challenge_confirm'"
            );

        assertThat(stepUp)
            .contains(
                "/api/v1/users/me/step-up/grants",
                "Authorization: `Bearer ${fixture.accessToken}`",
                "purpose: fixture.purpose",
                "code: fixture.code",
                "response.status === 200",
                "operation: 'step_up_grant'"
            );
    }

    @Test
    void shouldUseBoundedSmokeAndSteadyProfiles()
        throws IOException {

        String workload = Files.readString(
            K6_ROOT.resolve("lib/workload.js")
        );
        String compose = Files.readString(K6_ROOT.resolve("compose.yml"));

        assertThat(workload)
            .contains(
                "K6_PROFILE must be smoke or steady",
                "executor: 'shared-iterations'",
                "executor: 'constant-arrival-rate'",
                "'p(95)<=750'",
                "'p(99)<=1500'",
                "dropped_iterations"
            )
            .doesNotContain("http_req_duration{email:")
            .doesNotContain("http_req_duration{token:")
            .doesNotContain("http_req_duration{client:");

        assertThat(compose)
            .contains(
                "K6_PROFILE: ${K6_PROFILE:-smoke}",
                "K6_RATE: ${K6_RATE:-10}",
                "K6_DURATION: ${K6_DURATION:-120s}",
                "K6_FIXTURE_FILE: ${K6_FIXTURE_FILE:-/work/fixtures/credential-pool.example.json}"
            );
    }

    @Test
    void shouldKeepCredentialPoolsRuntimeOnly()
        throws IOException {

        String fixtures = Files.readString(
            K6_ROOT.resolve("lib/fixtures.js")
        );
        String example = Files.readString(
            K6_ROOT.resolve("fixtures/credential-pool.example.json")
        );
        String scenarios = Files.readString(K6_ROOT.resolve("SCENARIOS.md"));
        String gitignore = Files.readString(Path.of(".gitignore"));

        assertThat(fixtures)
            .contains(
                "K6_FIXTURE_FILE",
                "PLACEHOLDER_PREFIX = 'replace-runtime-'",
                "assertFixtureCapacity",
                "fixtureForIteration"
            );

        assertThat(example)
            .contains(
                "replace-runtime-challenge-token",
                "replace-runtime-mfa-proof",
                "replace-runtime-access-token"
            )
            .doesNotContain("eyJ")
            .doesNotContain("Bearer ");

        assertThat(scenarios)
            .contains(
                "performance/results/runtime/credential-pool.json",
                "must never be staged, committed",
                "Dataset generation and quota-pressure evidence remain separate"
            );

        assertThat(gitignore).contains("performance/results/");
    }

    @Test
    void shouldNeverUseSensitiveFixtureValuesAsMetricTags()
        throws IOException {

        String accountAction = Files.readString(
            K6_ROOT.resolve("scenarios/account-action-request.js")
        );
        String mfa = Files.readString(
            K6_ROOT.resolve("scenarios/mfa-challenge-confirm.js")
        );
        String stepUp = Files.readString(
            K6_ROOT.resolve("scenarios/step-up-grant.js")
        );

        assertThat(accountAction)
            .doesNotContain("email: email")
            .doesNotContain("tags: { email");

        assertThat(mfa)
            .doesNotContain("challengeToken: fixture.challengeToken,\n            tags")
            .doesNotContain("tags: { code")
            .doesNotContain("tags: { challenge");

        assertThat(stepUp)
            .doesNotContain("tags: { accessToken")
            .doesNotContain("tags: { code")
            .doesNotContain("tags: { purpose");
    }
}
