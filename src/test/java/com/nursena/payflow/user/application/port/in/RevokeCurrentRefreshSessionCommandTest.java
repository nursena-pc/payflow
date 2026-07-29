package com.nursena.payflow.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RevokeCurrentRefreshSessionCommandTest {

    @Test
    void shouldRetainRefreshToken() {
        RevokeCurrentRefreshSessionCommand command =
            new RevokeCurrentRefreshSessionCommand(
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
            new RevokeCurrentRefreshSessionCommand(
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
            new RevokeCurrentRefreshSessionCommand(
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
        RevokeCurrentRefreshSessionCommand command =
            new RevokeCurrentRefreshSessionCommand(
                "secret-refresh-token"
            );

        assertThat(command.toString())
            .isEqualTo(
                "RevokeCurrentRefreshSessionCommand[redacted]"
            )
            .doesNotContain(
                "secret-refresh-token"
            );
    }
}
