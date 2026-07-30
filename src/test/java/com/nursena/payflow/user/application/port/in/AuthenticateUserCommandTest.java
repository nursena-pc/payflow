
package com.nursena.payflow.user.application.port.in;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthenticateUserCommandTest {

    @Test
    void shouldRetainAuthenticationInput() {
        AuthenticateUserCommand command =
            new AuthenticateUserCommand(
                "nursena@example.com",
                "StrongPassword123!",
                "203.0.113.10"
            );

        assertThat(command.email())
            .isEqualTo(
                "nursena@example.com"
            );

        assertThat(command.rawPassword())
            .isEqualTo(
                "StrongPassword123!"
            );

        assertThat(command.clientAddress())
            .isEqualTo(
                "203.0.113.10"
            );
    }

    @Test
    void shouldRejectBlankClientAddress() {
        assertThatThrownBy(() ->
            new AuthenticateUserCommand(
                "nursena@example.com",
                "StrongPassword123!",
                " "
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "clientAddress must not be blank"
            );
    }

    @Test
    void shouldRedactPasswordAndClientAddress() {
        AuthenticateUserCommand command =
            new AuthenticateUserCommand(
                "nursena@example.com",
                "StrongPassword123!",
                "203.0.113.10"
            );

        assertThat(command.toString())
            .isEqualTo(
                "AuthenticateUserCommand[redacted]"
            )
            .doesNotContain(
                "StrongPassword123!",
                "203.0.113.10",
                "nursena@example.com"
            );
    }
}
