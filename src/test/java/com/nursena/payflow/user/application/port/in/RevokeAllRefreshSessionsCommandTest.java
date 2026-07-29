package com.nursena.payflow.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RevokeAllRefreshSessionsCommandTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "95000000-0000-0000-0000-000000000001"
        );

    @Test
    void shouldRetainAuthenticatedUserId() {
        RevokeAllRefreshSessionsCommand command =
            new RevokeAllRefreshSessionsCommand(
                USER_ID
            );

        assertThat(command.userId())
            .isEqualTo(USER_ID);
    }

    @Test
    void shouldRejectNullUserId() {
        assertThatThrownBy(
            () ->
                new RevokeAllRefreshSessionsCommand(
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "userId must not be null"
            );
    }

    @Test
    void shouldRedactUserIdFromStringRepresentation() {
        RevokeAllRefreshSessionsCommand command =
            new RevokeAllRefreshSessionsCommand(
                USER_ID
            );

        assertThat(command.toString())
            .isEqualTo(
                "RevokeAllRefreshSessionsCommand"
                    + "[redacted]"
            )
            .doesNotContain(
                USER_ID.toString()
            );
    }
}
