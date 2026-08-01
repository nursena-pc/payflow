package com.nursena.payflow.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveLogValueMaskerTest {

    private final SensitiveLogValueMasker masker =
        new SensitiveLogValueMasker();

    @Test
    void shouldMaskBearerAuthorizationAssignment() {
        String value =
            "Authorization: Bearer abc.def-123";

        assertThat(
            SensitiveLogValueMasker.redact(value)
        )
            .isEqualTo(
                "Authorization: [REDACTED]"
            );
    }

    @Test
    void shouldMaskJsonPassword() {
        String value =
            """
            {"email":"person@example.com","password":"secret-value"}
            """.trim();

        String redacted =
            SensitiveLogValueMasker.redact(value);

        assertThat(redacted)
            .contains(
                "\"password\":[REDACTED]"
            )
            .doesNotContain(
                "secret-value"
            );
    }

    @Test
    void shouldMaskOpaqueRefreshTokenAssignment() {
        String value =
            "refresh_token=opaque-refresh-token-123";

        assertThat(
            SensitiveLogValueMasker.redact(value)
        )
            .isEqualTo(
                "refresh_token=[REDACTED]"
            );
    }

    @Test
    void shouldMaskJwtAnywhere() {
        String jwt =
            "eyJhbGciOiJSUzI1NiJ9"
                + ".eyJzdWIiOiIxMjM0NTY3ODkwIn0"
                + ".signature-value";

        String redacted =
            SensitiveLogValueMasker.redact(
                "token received " + jwt
            );

        assertThat(redacted)
            .isEqualTo(
                "token received [REDACTED]"
            );
    }

    @Test
    void shouldMaskMultipleSecrets() {
        String value =
            "password=first apiKey=second client_secret=third";

        String redacted =
            SensitiveLogValueMasker.redact(value);

        assertThat(redacted)
            .contains(
                "password=[REDACTED]"
            )
            .contains(
                "apiKey=[REDACTED]"
            )
            .contains(
                "client_secret=[REDACTED]"
            )
            .doesNotContain(
                "first",
                "second",
                "third"
            );
    }

    @Test
    void shouldLeaveOrdinaryMessageUnchanged() {
        String value =
            "Wallet transfer completed successfully.";

        assertThat(
            masker.mask(
                null,
                value
            )
        )
            .isNull();

        assertThat(
            SensitiveLogValueMasker.redact(value)
        )
            .isEqualTo(value);
    }

    @Test
    void shouldLeaveCorrelationIdentifierUnchanged() {
        String value =
            "correlationId=request-123";

        assertThat(
            SensitiveLogValueMasker.redact(value)
        )
            .isEqualTo(value);
    }

    @Test
    void shouldIgnoreNonStringValues() {
        assertThat(
            masker.mask(
                null,
                42
            )
        )
            .isNull();
    }
}