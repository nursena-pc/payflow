package com.nursena.payflow.abuseprotection.adapter.out.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import org.junit.jupiter.api.Test;

class AbuseProtectionPropertiesTest {

    @Test
    void shouldResolveEveryWorkflowPolicy() {
        AbuseProtectionProperties properties =
            properties(true);

        for (
            AbuseProtectionWorkflow workflow
                : AbuseProtectionWorkflow.values()
        ) {
            assertThat(
                properties.policyFor(workflow).enabled()
            ).isTrue();

            assertThat(
                properties
                    .policyFor(workflow)
                    .dependencyFailureMode()
            ).isEqualTo(
                AbuseProtectionFailureMode.FAIL_CLOSED
            );
        }
    }

    @Test
    void shouldApplyGlobalDisableWithoutLosingPolicy() {
        AbuseProtectionProperties properties =
            properties(false);

        assertThat(
            properties
                .policyFor(
                    AbuseProtectionWorkflow.REGISTRATION
                )
                .enabled()
        ).isFalse();

        assertThat(
            properties
                .policyFor(
                    AbuseProtectionWorkflow.REGISTRATION
                )
                .window()
        ).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void shouldRequireEveryWorkflowPolicy() {
        AbuseProtectionProperties.WorkflowPolicyProperties policy =
            policy();

        assertThatThrownBy(() ->
            new AbuseProtectionProperties(
                true,
                policy,
                null,
                policy,
                policy,
                policy
            )
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage(
                "emailVerificationRequest policy "
                    + "must not be null"
            );
    }

    private static AbuseProtectionProperties properties(
        boolean enabled
    ) {
        AbuseProtectionProperties.WorkflowPolicyProperties policy =
            policy();

        return new AbuseProtectionProperties(
            enabled,
            policy,
            policy,
            policy,
            policy,
            policy
        );
    }

    private static AbuseProtectionProperties.WorkflowPolicyProperties
        policy() {
        return new AbuseProtectionProperties
            .WorkflowPolicyProperties(
                true,
                Duration.ofMinutes(15),
                5,
                20,
                AbuseProtectionFailureMode.FAIL_CLOSED
            );
    }
}
