package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015PerformanceDatasetAndQuotaContractTest {

    private static final Path K6_ROOT =
        Path.of("performance", "k6");

    @Test
    void shouldKeepGeneratedCredentialsRuntimeOnly()
        throws IOException {

        String generator = Files.readString(
            K6_ROOT.resolve("setup/generate-credential-pool.ps1")
        );
        String gitignore = Files.readString(Path.of(".gitignore"));

        assertThat(generator)
            .contains(
                "performance\\results\\runtime",
                "credential-pool.json",
                "git check-ignore --quiet --no-index",
                "[ValidateRange(1, 4)]",
                "$OutputFullPath.Substring(",
                "$RepositoryPrefix.Length",
                "$MfaFixtures.ToArray()",
                "$StepUpFixtures.ToArray()",
                "Sensitive fixture values are not printed.",
                "Remove-Item -LiteralPath $OutputFullPath -Force"
            )
            .doesNotContain(
                "[IO.Path]::GetRelativePath",
                "mfaChallengeConfirm = @($MfaFixtures)",
                "stepUpGrant = @($StepUpFixtures)",
                "Write-Host $Enrollment.secret",
                "Write-Host $Challenge.challengeToken",
                "Write-Host $Authenticated.accessToken",
                "Write-Host $RecoveryCodes"
            );

        assertThat(gitignore).contains("performance/results/");
    }

    @Test
    void shouldGenerateFixturesThroughReviewedHttpContracts()
        throws IOException {

        String generator = Files.readString(
            K6_ROOT.resolve("setup/generate-credential-pool.ps1")
        );

        assertThat(generator)
            .contains(
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/email-verification/confirm",
                "/api/v1/messages?limit=50",
                "$Summaries = @($Mailbox.messages)",
                "$Recipient.Address -ceq $Email",
                "$MessageId = [string] $Selected.ID",
                "$EncodedMessageId = [Uri]::EscapeDataString($MessageId)",
                "/api/v1/message/$EncodedMessageId",
                "$Body = [string] $Message.Text",
                "Test-CanonicalAccountActionCredential",
                "$Credential.Length -ne 43",
                "[StringComparison]::Ordinal",
                "token=([A-Za-z0-9_-]{43})",
                "exactly one canonical credential link",
                "/api/v1/users/me/mfa/enrollment",
                "/api/v1/users/me/mfa/enrollment/confirm",
                "/api/v1/auth/mfa/challenges/confirm",
                "@example.invalid",
                "MailpitUrl",
                "Registration already issues the first "
                    + "email-verification credential.",
                "$Step returned HTTP status $FailureStatus.",
                "challengeToken = [string] $Challenge.challengeToken",
                "purpose = 'mfa-disable'"
            )
            .doesNotContain(
                "-Path '/api/v1/auth/email-verification/requests'",
                "/api/v1/search?query=",
                "/view/",
                "token=([A-Za-z0-9_%\\-]+)",
                "token=([A-Za-z0-9%_-]+)",
                "$Credential -notmatch",
                "$Credential -cnotmatch",
                "$Canonical -ceq $Credential",
                "$MessageId -notmatch",
                "$MessageId -match"
            );
    }

    @Test
    void shouldUseTheReviewedTotpContractWithoutExternalService()
        throws IOException {

        String totp = Files.readString(
            K6_ROOT.resolve("setup/totp.ps1")
        );

        assertThat(totp)
            .contains(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567",
                "$Normalized -cnotmatch '^[A-Z2-7]+$'",
                "HMACSHA1",
                "ToUnixTimeSeconds() / 30",
                "% 1000000",
                "'{0:D6}'",
                "Wait-PayFlowStableTotpWindow"
            )
            .doesNotContain(
                "$Normalized -notmatch '^[A-Z2-7]+$'",
                "Invoke-WebRequest"
            );
    }

    @Test
    void shouldApplyBoundedConcurrentQuotaPressure()
        throws IOException {

        String scenario = Files.readString(
            K6_ROOT.resolve(
                "scenarios/account-action-quota-pressure.js"
            )
        );
        String runner = Files.readString(K6_ROOT.resolve("run.ps1"));
        String compose = Files.readString(K6_ROOT.resolve("compose.yml"));
        String workload = Files.readString(
            K6_ROOT.resolve("lib/workload.js")
        );

        assertThat(scenario)
            .contains(
                "const REQUEST_COUNT = 40",
                "executor: 'per-vu-iterations'",
                "vus: REQUEST_COUNT",
                "iterations: 1",
                "syntheticAccountActionEmail",
                "/api/v1/auth/email-verification/requests",
                "response.status === 202",
                "operation: 'account_action_quota_pressure'"
            )
            .doesNotContain("tags: { email");

        assertThat(runner)
            .contains(
                "'account-action-quota-pressure'",
                "/work/scenarios/account-action-quota-pressure.js",
                "reserved execution option"
            );

        assertThat(compose)
            .contains(
                "PAYFLOW_K6_DURATION: ${K6_DURATION:-120s}"
            )
            .doesNotContain(
                "\n      K6_DURATION: ${K6_DURATION:-120s}"
            );

        assertThat(workload)
            .contains("__ENV.PAYFLOW_K6_DURATION")
            .doesNotContain("__ENV.K6_DURATION");
    }

    @Test
    void shouldValidateQuotaFromBoundedAggregateMetrics()
        throws IOException {

        String validator = Files.readString(
            K6_ROOT.resolve("validate-quota-pressure.ps1")
        );
        String scenarios = Files.readString(K6_ROOT.resolve("SCENARIOS.md"));
        String normalizedScenarios = scenarios.replaceAll("\\s+", " ");

        assertThat(validator)
            .contains(
                "payflow_security_abuse_protection_decisions_total",
                "workflow=\"email-verification-request\"",
                "$AllowedDelta -ne 20",
                "$BlockedClientDelta -ne 20",
                "dependency_bypass",
                "/actuator/prometheus"
            )
            .doesNotContain(
                "payflow:security:abuse:v1:",
                "redis-cli"
            );

        assertThat(normalizedScenarios)
            .contains(
                "fresh isolated Compose project and Redis volume",
                "twenty `allowed/none`",
                "twenty `blocked/client`",
                "not accepted steady-state",
                "Delete the sensitive runtime fixture",
                "does not request a second email-verification credential"
            );
    }
}
