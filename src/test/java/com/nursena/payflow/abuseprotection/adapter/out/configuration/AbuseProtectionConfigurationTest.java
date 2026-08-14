package com.nursena.payflow.abuseprotection.adapter.out.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicyProvider;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AbuseProtectionConfigurationTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                AbuseProtectionConfiguration.class
            );

    @Test
    void shouldBindValidatedWorkflowPolicies() {
        contextRunner
            .withPropertyValues(validProperties())
            .run(context -> {
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(
                        AbuseProtectionProperties.class
                    )
                    .hasSingleBean(
                        AbuseProtectionPolicyProvider.class
                    );

                AbuseProtectionPolicyProvider provider =
                    context.getBean(
                        AbuseProtectionPolicyProvider.class
                    );

                assertThat(
                    provider
                        .policyFor(
                            AbuseProtectionWorkflow
                                .EMAIL_VERIFICATION_REQUEST
                        )
                        .window()
                ).isEqualTo(Duration.ofMinutes(15));

                assertThat(
                    provider
                        .policyFor(
                            AbuseProtectionWorkflow
                                .EMAIL_VERIFICATION_REQUEST
                        )
                        .dependencyFailureMode()
                ).isEqualTo(
                    AbuseProtectionFailureMode.FAIL_CLOSED
                );
            });
    }

    @Test
    void shouldRejectInvalidWorkflowConfiguration() {
        String[] properties = validProperties();
        properties[2] =
            "payflow.security.abuse-protection."
                + "registration.window=500ms";

        contextRunner
            .withPropertyValues(properties)
            .run(context ->
                assertThat(context).hasFailed()
            );
    }

    private static String[] validProperties() {
        return new String[] {
            "payflow.security.abuse-protection.enabled=true",
            workflowProperty(
                "registration",
                "enabled=true"
            ),
            workflowProperty(
                "registration",
                "window=15m"
            ),
            workflowProperty(
                "registration",
                "identity-limit=5"
            ),
            workflowProperty(
                "registration",
                "client-limit=20"
            ),
            workflowProperty(
                "registration",
                "dependency-failure-mode=FAIL_CLOSED"
            ),
            workflowProperty(
                "email-verification-request",
                "enabled=true"
            ),
            workflowProperty(
                "email-verification-request",
                "window=15m"
            ),
            workflowProperty(
                "email-verification-request",
                "identity-limit=3"
            ),
            workflowProperty(
                "email-verification-request",
                "client-limit=20"
            ),
            workflowProperty(
                "email-verification-request",
                "dependency-failure-mode=FAIL_CLOSED"
            ),
            workflowProperty(
                "password-recovery-request",
                "enabled=true"
            ),
            workflowProperty(
                "password-recovery-request",
                "window=15m"
            ),
            workflowProperty(
                "password-recovery-request",
                "identity-limit=3"
            ),
            workflowProperty(
                "password-recovery-request",
                "client-limit=20"
            ),
            workflowProperty(
                "password-recovery-request",
                "dependency-failure-mode=FAIL_CLOSED"
            ),
            workflowProperty(
                "mfa-login-challenge-confirmation",
                "enabled=true"
            ),
            workflowProperty(
                "mfa-login-challenge-confirmation",
                "window=5m"
            ),
            workflowProperty(
                "mfa-login-challenge-confirmation",
                "identity-limit=5"
            ),
            workflowProperty(
                "mfa-login-challenge-confirmation",
                "client-limit=20"
            ),
            workflowProperty(
                "mfa-login-challenge-confirmation",
                "dependency-failure-mode=FAIL_CLOSED"
            ),
            workflowProperty(
                "step-up-grant-issuance",
                "enabled=true"
            ),
            workflowProperty(
                "step-up-grant-issuance",
                "window=5m"
            ),
            workflowProperty(
                "step-up-grant-issuance",
                "identity-limit=5"
            ),
            workflowProperty(
                "step-up-grant-issuance",
                "client-limit=20"
            ),
            workflowProperty(
                "step-up-grant-issuance",
                "dependency-failure-mode=FAIL_CLOSED"
            )
        };
    }

    private static String workflowProperty(
        String workflow,
        String property
    ) {
        return "payflow.security.abuse-protection."
            + workflow
            + "."
            + property;
    }
}
