package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW =
        Instant.parse("2026-07-12T12:00:00Z");

    @Test
    void shouldRegisterActiveUserWithUserRole() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        assertThat(user.id()).isNotNull();
        assertThat(user.email().value()).isEqualTo("nursena@example.com");
        assertThat(user.role()).isEqualTo(UserRole.USER);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.createdAt()).isEqualTo(NOW);
        assertThat(user.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectBlankPasswordHash() {
        assertThatThrownBy(() -> User.register(
            EmailAddress.of("nursena@example.com"),
            " ",
            NOW
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("passwordHash must not be blank");
    }

    @Test
    void shouldSuspendUser() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        Instant suspensionTime =
            Instant.parse("2026-07-12T13:00:00Z");

        user.suspend(suspensionTime);

        assertThat(user.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.updatedAt()).isEqualTo(suspensionTime);
    }
}
