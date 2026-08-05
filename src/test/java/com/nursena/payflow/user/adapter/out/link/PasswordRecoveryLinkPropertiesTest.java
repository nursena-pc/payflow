package com.nursena.payflow.user.adapter.out.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class PasswordRecoveryLinkPropertiesTest {

    @Test
    void shouldAcceptAbsoluteHttpAndHttpsUris() {
        assertThat(
            new PasswordRecoveryLinkProperties(
                URI.create(
                    "https://app.payflow.local/recover-password"
                )
            ).passwordRecoveryConfirmationUri()
        )
            .isEqualTo(
                URI.create(
                    "https://app.payflow.local/recover-password"
                )
            );

        assertThat(
            new PasswordRecoveryLinkProperties(
                URI.create(
                    "http://localhost:3000/recover-password"
                )
            ).passwordRecoveryConfirmationUri()
        )
            .isEqualTo(
                URI.create(
                    "http://localhost:3000/recover-password"
                )
            );
    }

    @Test
    void shouldRejectUnsafeOrRequestShapedUris() {
        assertRejected("/recover-password");
        assertRejected(
            "ftp://app.payflow.local/recover-password"
        );
        assertRejected(
            "https://user@app.payflow.local/recover-password"
        );
        assertRejected(
            "https://app.payflow.local/recover-password?token=value"
        );
        assertRejected(
            "https://app.payflow.local/recover-password#fragment"
        );
    }

    private static void assertRejected(String value) {
        assertThatThrownBy(() ->
            new PasswordRecoveryLinkProperties(
                URI.create(value)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "absolute HTTP(S) URI"
            );
    }
}
