package com.nursena.payflow.abuseprotection.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import org.junit.jupiter.api.Test;

class AbuseProtectionKeyFactoryTest {

    @Test
    void shouldCreateBoundedDomainSeparatedKeys() {
        String identityKey =
            AbuseProtectionKeyFactory.identityKey(
                AbuseProtectionWorkflow.REGISTRATION,
                "nursena@example.com"
            );

        String clientKey =
            AbuseProtectionKeyFactory.clientKey(
                AbuseProtectionWorkflow.REGISTRATION,
                "203.0.113.10"
            );

        assertThat(identityKey)
            .startsWith(
                "payflow:security:abuse:v1:registration:identity:"
            )
            .doesNotContain("nursena@example.com")
            .matches(".*:[0-9a-f]{64}$")
            .hasSizeLessThanOrEqualTo(180);

        assertThat(clientKey)
            .startsWith(
                "payflow:security:abuse:v1:registration:client:"
            )
            .doesNotContain("203.0.113.10")
            .matches(".*:[0-9a-f]{64}$")
            .hasSizeLessThanOrEqualTo(180)
            .isNotEqualTo(identityKey);
    }

    @Test
    void shouldSeparateWorkflowDomains() {
        assertThat(
            AbuseProtectionKeyFactory.identityKey(
                AbuseProtectionWorkflow.REGISTRATION,
                "same-subject"
            )
        ).isNotEqualTo(
            AbuseProtectionKeyFactory.identityKey(
                AbuseProtectionWorkflow.PASSWORD_RECOVERY_REQUEST,
                "same-subject"
            )
        );
    }
}
