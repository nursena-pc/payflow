package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in
    .ConfirmEmailVerificationCommand;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
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
class ConfirmEmailVerificationServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "827f0c7d-b4ca-48b6-bef4-808e1a59ae2d"
        );

    private static final Instant NOW =
        Instant.parse("2026-08-05T12:00:00Z");

    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    @Mock
    private AccountActionCredentialConsumer
        credentialConsumer;

    @Mock
    private UserRepositoryPort userRepository;

    private ConfirmEmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new ConfirmEmailVerificationService(
            credentialConsumer,
            userRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldConsumeCredentialAndVerifyEmailExactlyOnce() {
        User user = unverifiedUser(UserStatus.ACTIVE);

        when(credentialConsumer.consume(
            CREDENTIAL,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION
        ))
            .thenReturn(USER_ID);
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        service.confirm(
            new ConfirmEmailVerificationCommand(
                CREDENTIAL
            )
        );

        ArgumentCaptor<User> captor =
            ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().isEmailVerified())
            .isTrue();
        assertThat(captor.getValue().emailVerifiedAt())
            .isEqualTo(NOW);
    }

    @Test
    void shouldRejectCredentialForAlreadyVerifiedUser() {
        when(credentialConsumer.consume(
            CREDENTIAL,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION
        ))
            .thenReturn(USER_ID);
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(verifiedUser()));

        assertThatThrownBy(() ->
            service.confirm(
                new ConfirmEmailVerificationCommand(
                    CREDENTIAL
                )
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            );

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectCredentialForUnavailableUser() {
        when(credentialConsumer.consume(
            CREDENTIAL,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION
        ))
            .thenReturn(USER_ID);
        when(userRepository.findById(USER_ID))
            .thenReturn(
                Optional.of(
                    unverifiedUser(UserStatus.CLOSED)
                )
            );

        assertThatThrownBy(() ->
            service.confirm(
                new ConfirmEmailVerificationCommand(
                    CREDENTIAL
                )
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            );

        verify(userRepository, never()).save(any());
    }

    private static User unverifiedUser(
        UserStatus status
    ) {
        return User.rehydrate(
            USER_ID,
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            UserRole.USER,
            status,
            null,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60)
        );
    }

    private static User verifiedUser() {
        return User.rehydrate(
            USER_ID,
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            UserRole.USER,
            UserStatus.ACTIVE,
            NOW.minusSeconds(30),
            NOW.minusSeconds(60),
            NOW.minusSeconds(30)
        );
    }
}
