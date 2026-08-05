package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.UUID;

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
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.emailVerifiedAt()).isNull();
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

    @Test
    void shouldVerifyEmailExactlyOnce() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        Instant verificationTime =
            Instant.parse("2026-07-12T13:00:00Z");

        assertThat(user.verifyEmail(verificationTime)).isTrue();
        assertThat(user.verifyEmail(
            verificationTime.plusSeconds(60)
        )).isFalse();

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.emailVerifiedAt())
            .isEqualTo(verificationTime);
        assertThat(user.updatedAt())
            .isEqualTo(verificationTime);
    }

    @Test
    void shouldRestoreVerifiedEmailState() {
        Instant verificationTime =
            Instant.parse("2026-07-12T13:00:00Z");

        User user = User.rehydrate(
            UUID.randomUUID(),
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            UserRole.USER,
            UserStatus.ACTIVE,
            verificationTime,
            NOW,
            verificationTime
        );

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.emailVerifiedAt())
            .isEqualTo(verificationTime);
    }

    @Test
    void shouldRejectVerificationStateBeforeRegistration() {
        assertThatThrownBy(() -> User.rehydrate(
            UUID.randomUUID(),
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            UserRole.USER,
            UserStatus.ACTIVE,
            NOW.minusSeconds(1),
            NOW,
            NOW
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "emailVerifiedAt must not be before createdAt"
            );
    }

    @Test
    void shouldRejectVerificationBeforeRegistration() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        assertThatThrownBy(() -> user.verifyEmail(
            NOW.minusSeconds(1)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("now must not be before createdAt");
    }

    @Test
    void shouldChangePasswordOnlyThroughPackageBoundary()
        throws NoSuchMethodException {

        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$old-hashed-password",
            NOW
        );

        Instant recoveryTime =
            Instant.parse("2026-07-12T14:00:00Z");

        user.changePassword(
            "$2a$12$replacement-hashed-password",
            recoveryTime
        );

        assertThat(user.passwordHash())
            .isEqualTo("$2a$12$replacement-hashed-password");
        assertThat(user.updatedAt()).isEqualTo(recoveryTime);
        assertThat(Modifier.isPublic(
            User.class
                .getDeclaredMethod(
                    "changePassword",
                    String.class,
                    Instant.class
                )
                .getModifiers()
        )).isFalse();
    }

    @Test
    void shouldRejectBlankReplacementPasswordHash() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$old-hashed-password",
            NOW
        );

        assertThatThrownBy(() -> user.changePassword(
            " ",
            NOW.plusSeconds(60)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("passwordHash must not be blank");
    }
}
