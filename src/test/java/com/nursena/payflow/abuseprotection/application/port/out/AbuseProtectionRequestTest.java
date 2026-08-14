package com.nursena.payflow.abuseprotection.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.clientcontext.domain.IpAddress;
import org.junit.jupiter.api.Test;

class AbuseProtectionRequestTest {

    @Test
    void shouldRetainNormalizedInputs() {
        AbuseProtectionRequest request =
            new AbuseProtectionRequest(
                AbuseProtectionWorkflow.REGISTRATION,
                "nursena@example.com",
                IpAddress.parse("203.0.113.10")
            );

        assertThat(request.normalizedIdentity())
            .isEqualTo("nursena@example.com");
        assertThat(request.effectiveClientAddress().value())
            .isEqualTo("203.0.113.10");
    }

    @Test
    void shouldRejectBlankOrUntrimmedIdentity() {
        assertThatThrownBy(() -> request(" "))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> request(" identity "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static AbuseProtectionRequest request(
        String identity
    ) {
        return new AbuseProtectionRequest(
            AbuseProtectionWorkflow.REGISTRATION,
            identity,
            IpAddress.parse("203.0.113.10")
        );
    }
}
