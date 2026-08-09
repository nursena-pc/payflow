package com.nursena.payflow.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentCommand;
import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentResult;
import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentCommand;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import org.junit.jupiter.api.Test;

class MfaEnrollmentSensitiveValueRedactionTest {

    private static final String PASSWORD = "CurrentPassword123!";
    private static final String CODE = "123456";
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private static final String PROVISIONING_URI =
        "otpauth://totp/PayFlow:user%40example.com?secret="
            + SECRET
            + "&issuer=PayFlow&algorithm=SHA1&digits=6&period=30";

    @Test
    void shouldRedactCredentialsAndProvisioningMaterialFromStringRepresentations() {
        var userId = UUID.randomUUID();
        var expiresAt = Instant.parse("2026-08-08T10:10:00Z");

        String recoveryCode = "AbCdEfGhIjKlMnOpQrStUv";

        Object[] sensitiveObjects = {
            new BeginMfaEnrollmentRequest(PASSWORD),
            new BeginMfaEnrollmentCommand(userId, PASSWORD),
            new ConfirmMfaEnrollmentRequest(CODE),
            new ConfirmMfaEnrollmentCommand(userId, CODE),
            new BeginMfaEnrollmentResult(
                MfaLifecycleState.PENDING,
                SECRET,
                PROVISIONING_URI,
                expiresAt
            ),
            new MfaEnrollmentResponse(
                MfaLifecycleState.PENDING,
                SECRET,
                PROVISIONING_URI,
                expiresAt
            ),
            new com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentResult(
                MfaLifecycleState.ENABLED,
                expiresAt,
                List.of(recoveryCode)
            ),
            new MfaEnrollmentConfirmationResponse(
                MfaLifecycleState.ENABLED,
                expiresAt,
                List.of(recoveryCode)
            )
        };

        for (Object sensitiveObject : sensitiveObjects) {
            assertThat(sensitiveObject.toString())
                .doesNotContain(PASSWORD)
                .doesNotContain(CODE)
                .doesNotContain(SECRET)
                .doesNotContain(PROVISIONING_URI)
                .doesNotContain(recoveryCode)
                .containsIgnoringCase("redacted");
        }
    }
}
