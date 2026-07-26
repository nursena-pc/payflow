package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in.AuthenticateUserResult;
import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.PasswordVerificationPort;
import com.nursena.payflow.user.application.port.out.AccessTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final Instant NOW =
        Instant.parse("2026-07-12T12:00:00Z");

    private static final Instant EXPIRES_AT =
        Instant.parse("2026-07-12T12:15:00Z");

    private static final String RAW_PASSWORD =
        "StrongPassword123!";

    private static final String PASSWORD_HASH =
        "$2a$12$hashed-password";

    private static final String ACCESS_TOKEN =
        "generated.jwt.token";

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordVerificationPort passwordVerification;

    @Mock
    private AccessTokenGenerationPort accessTokenGeneration;

    private AuthenticateUserService authenticateUserService;

    @BeforeEach
    void setUp() {
        authenticateUserService = new AuthenticateUserService(
            userRepository,
            passwordVerification,
            accessTokenGeneration
        );
    }

    @Test
    void shouldAuthenticateActiveUser() {
        EmailAddress email =
            EmailAddress.of("nursena@example.com");

        User user = activeUser(email);

        when(userRepository.findByEmail(email))
            .thenReturn(Optional.of(user));
        when(passwordVerification.matches(
            RAW_PASSWORD,
            PASSWORD_HASH
        )).thenReturn(true);
        when(accessTokenGeneration.generate(user))
            .thenReturn(new GeneratedAccessToken(
                ACCESS_TOKEN,
                EXPIRES_AT
            ));

        AuthenticateUserResult result =
            authenticateUserService.authenticate(
                new AuthenticateUserCommand(
                    "  NURSENA@EXAMPLE.COM  ",
                    RAW_PASSWORD
                )
            );

        assertThat(result.accessToken())
            .isEqualTo(ACCESS_TOKEN);
        assertThat(result.expiresAt())
            .isEqualTo(EXPIRES_AT);

        verify(userRepository).findByEmail(email);
        verify(passwordVerification).matches(
            RAW_PASSWORD,
            PASSWORD_HASH
        );
        verify(accessTokenGeneration).generate(user);
    }

    @Test
    void shouldRejectUnknownEmail() {
        EmailAddress email =
            EmailAddress.of("unknown@example.com");

        when(userRepository.findByEmail(email))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                new AuthenticateUserCommand(
                    "unknown@example.com",
                    RAW_PASSWORD
                )
            ))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Email or password is incorrect.");

        verify(userRepository).findByEmail(email);
        verifyNoInteractions(
            passwordVerification,
            accessTokenGeneration
        );
    }

    @Test
    void shouldRejectIncorrectPassword() {
        EmailAddress email =
            EmailAddress.of("nursena@example.com");

        User user = activeUser(email);

        when(userRepository.findByEmail(email))
            .thenReturn(Optional.of(user));
        when(passwordVerification.matches(
            "IncorrectPassword123!",
            PASSWORD_HASH
        )).thenReturn(false);

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                new AuthenticateUserCommand(
                    "nursena@example.com",
                    "IncorrectPassword123!"
                )
            ))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Email or password is incorrect.");

        verify(accessTokenGeneration, never()).generate(user);
    }

    @Test
    void shouldRejectUnavailableUserAccount() {
        EmailAddress email =
            EmailAddress.of("nursena@example.com");

        User user = unavailableUser(email);

        when(userRepository.findByEmail(email))
            .thenReturn(Optional.of(user));
        when(passwordVerification.matches(
            RAW_PASSWORD,
            PASSWORD_HASH
        )).thenReturn(true);

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                new AuthenticateUserCommand(
                    "nursena@example.com",
                    RAW_PASSWORD
                )
            ))
            .isInstanceOf(
                UserAccountUnavailableException.class
            )
            .hasMessage(
                "User account is not available for authentication."
            );

        verify(accessTokenGeneration, never()).generate(user);
    }

    private static User activeUser(EmailAddress email) {
        return User.rehydrate(
            USER_ID,
            email,
            PASSWORD_HASH,
            UserRole.USER,
            UserStatus.ACTIVE,
            NOW,
            NOW
        );
    }

    private static User unavailableUser(EmailAddress email) {
        return User.rehydrate(
            USER_ID,
            email,
            PASSWORD_HASH,
            UserRole.USER,
            UserStatus.SUSPENDED,
            NOW,
            NOW
        );
    }
}
