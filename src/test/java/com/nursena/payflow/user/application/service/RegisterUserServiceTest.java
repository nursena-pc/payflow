package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.RegisterUserCommand;
import com.nursena.payflow.user.application.port.out.PasswordHashingPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.EmailAlreadyRegisteredException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-07-12T12:00:00Z");

    private static final String RAW_PASSWORD =
        "StrongPassword123!";

    private static final String PASSWORD_HASH =
        "$2a$12$hashed-password";

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordHashingPort passwordHashing;

    private RegisterUserService registerUserService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        registerUserService = new RegisterUserService(
            userRepository,
            passwordHashing,
            clock
        );
    }

    @Test
    void shouldRegisterUser() {
        RegisterUserCommand command = new RegisterUserCommand(
            "  Nursena@Example.COM  ",
            RAW_PASSWORD
        );

        when(userRepository.existsByEmail(any(EmailAddress.class)))
            .thenReturn(false);
        when(passwordHashing.hash(RAW_PASSWORD))
            .thenReturn(PASSWORD_HASH);
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UUID userId = registerUserService.register(command);

        ArgumentCaptor<User> userCaptor =
            ArgumentCaptor.forClass(User.class);

        verify(userRepository)
            .existsByEmail(EmailAddress.of("nursena@example.com"));
        verify(passwordHashing).hash(RAW_PASSWORD);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(userId).isEqualTo(savedUser.id());
        assertThat(savedUser.email().value())
            .isEqualTo("nursena@example.com");
        assertThat(savedUser.passwordHash())
            .isEqualTo(PASSWORD_HASH);
        assertThat(savedUser.role())
            .isEqualTo(UserRole.USER);
        assertThat(savedUser.status())
            .isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.isEmailVerified()).isFalse();
        assertThat(savedUser.emailVerifiedAt()).isNull();
        assertThat(savedUser.createdAt())
            .isEqualTo(NOW);
    }

    @Test
    void shouldRejectAlreadyRegisteredEmail() {
        EmailAddress email =
            EmailAddress.of("nursena@example.com");

        when(userRepository.existsByEmail(email))
            .thenReturn(true);

        RegisterUserCommand command = new RegisterUserCommand(
            "nursena@example.com",
            RAW_PASSWORD
        );

        assertThatThrownBy(() -> registerUserService.register(command))
            .isInstanceOf(EmailAlreadyRegisteredException.class)
            .hasMessage(
                "A user with this email address already exists."
            );

        verify(userRepository).existsByEmail(email);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordHashing);
    }
}
