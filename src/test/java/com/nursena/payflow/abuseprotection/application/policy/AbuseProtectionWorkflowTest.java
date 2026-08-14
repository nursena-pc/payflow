package com.nursena.payflow.abuseprotection.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class AbuseProtectionWorkflowTest {

    @Test
    void shouldExposeBoundedStableConfigurationKeys() {
        assertThat(
            Arrays.stream(
                AbuseProtectionWorkflow.values()
            )
                .map(
                    AbuseProtectionWorkflow::configurationKey
                )
        )
            .containsExactlyInAnyOrder(
                "registration",
                "email-verification-request",
                "password-recovery-request",
                "mfa-login-challenge-confirmation",
                "step-up-grant-issuance"
            )
            .allMatch(key ->
                key.length() <= 64
                    && key.matches(
                        "[a-z0-9]+(?:-[a-z0-9]+)*"
                    )
            )
            .doesNotHaveDuplicates();
    }
}
