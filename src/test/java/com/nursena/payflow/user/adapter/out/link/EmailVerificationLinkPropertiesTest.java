package com.nursena.payflow.user.adapter.out.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class EmailVerificationLinkPropertiesTest {

    @Test
    void shouldAcceptAbsoluteHttpAndHttpsUris() {
        assertThat(
            new EmailVerificationLinkProperties(
                URI.create(
                    "https://app.payflow.local/verify-email"
                )
            ).emailVerificationConfirmationUri()
        )
            .isEqualTo(
                URI.create(
                    "https://app.payflow.local/verify-email"
                )
            );

        assertThat(
            new EmailVerificationLinkProperties(
                URI.create(
                    "http://localhost:3000/verify-email"
                )
            ).emailVerificationConfirmationUri()
        )
            .isEqualTo(
                URI.create(
                    "http://localhost:3000/verify-email"
                )
            );
    }

    @Test
    void shouldRejectUnsafeOrRequestShapedUris() {
        assertRejected("/verify-email");
        assertRejected("ftp://app.payflow.local/verify-email");
        assertRejected("https://user@app.payflow.local/verify-email");
        assertRejected("https://app.payflow.local/verify-email?token=value");
        assertRejected("https://app.payflow.local/verify-email#fragment");
    }

    private static void assertRejected(String value) {
        assertThatThrownBy(() ->
            new EmailVerificationLinkProperties(
                URI.create(value)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "absolute HTTP(S) URI"
            );
    }
}
