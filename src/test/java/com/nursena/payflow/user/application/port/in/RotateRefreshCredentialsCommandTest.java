package com.nursena.payflow.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RotateRefreshCredentialsCommandTest {

    @Test
    void shouldRetainRefreshToken() {
        RotateRefreshCredentialsCommand command =
            new RotateRefreshCredentialsCommand(
                "opaque-refresh-token"
            );

        assertThat(command.refreshToken())
            .isEqualTo(
                "opaque-refresh-token"
            );
    }

    @Test
    void shouldRequireRefreshToken() {
        assertThatThrownBy(() ->
            new RotateRefreshCredentialsCommand(
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "refreshToken must not be null"
            );
    }

    @Test
    void shouldRejectBlankRefreshToken() {
        assertThatThrownBy(() ->
            new RotateRefreshCredentialsCommand(
                " "
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "refreshToken must not be blank"
            );
    }

    @Test
    void shouldRedactRefreshTokenFromToString() {
        RotateRefreshCredentialsCommand command =
            new RotateRefreshCredentialsCommand(
                "secret-refresh-token"
            );

        assertThat(command.toString())
            .isEqualTo(
                "RotateRefreshCredentialsCommand[redacted]"
            )
            .doesNotContain(
                "secret-refresh-token"
            );
    }
}
