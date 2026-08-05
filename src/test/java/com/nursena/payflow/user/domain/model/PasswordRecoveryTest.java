package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class PasswordRecoveryTest {

    private static final Instant REGISTERED_AT =
        Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void shouldReplaceHashThroughExplicitRecoveryOperation()
        throws NoSuchMethodException {

        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$old-hashed-password",
            REGISTERED_AT
        );
        Instant recoveredAt =
            REGISTERED_AT.plusSeconds(60);

        PasswordRecovery.replacePassword(
            user,
            "$2a$12$replacement-hashed-password",
            recoveredAt
        );

        assertThat(user.passwordHash()).isEqualTo(
            "$2a$12$replacement-hashed-password"
        );
        assertThat(user.updatedAt()).isEqualTo(recoveredAt);
        assertThat(Modifier.isPublic(
            User.class.getDeclaredMethod(
                "changePassword",
                String.class,
                Instant.class
            ).getModifiers()
        )).isFalse();
    }

    @Test
    void shouldRejectNullUser() {
        assertThatThrownBy(() ->
            PasswordRecovery.replacePassword(
                null,
                "$2a$12$replacement-hashed-password",
                REGISTERED_AT
            )
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage("user must not be null");
    }
}
