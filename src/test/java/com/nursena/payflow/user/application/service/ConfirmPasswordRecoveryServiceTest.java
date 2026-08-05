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
    .ConfirmPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.out
    .PasswordHashingPort;
import com.nursena.payflow.user.application.port.out
    .RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model
    .RefreshTokenFamilyRevocationReason;
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
class ConfirmPasswordRecoveryServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "8af903dc-14f7-47bb-a80e-d290f0ec1b15"
        );

    private static final Instant NOW =
        Instant.parse("2026-08-05T12:00:00Z");

    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final String NEW_PASSWORD =
        "ReplacementPassword123!";

    private static final String NEW_PASSWORD_HASH =
        "$2a$12$replacement-hashed-password";

    @Mock
    private AccountActionCredentialConsumer
        credentialConsumer;

    @Mock
    private PasswordHashingPort passwordHashing;

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private RefreshTokenFamilyRepositoryPort
        familyRepository;

    private ConfirmPasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new ConfirmPasswordRecoveryService(
            credentialConsumer,
            passwordHashing,
            userRepository,
            familyRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldReplacePasswordAndRevokeAllRefreshFamilies() {
        User user = verifiedUser(UserStatus.ACTIVE);

        when(passwordHashing.hash(NEW_PASSWORD))
            .thenReturn(NEW_PASSWORD_HASH);
        when(credentialConsumer.consume(
            CREDENTIAL,
            AccountActionCredentialPurpose
                .PASSWORD_RECOVERY
        ))
            .thenReturn(USER_ID);
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        service.confirm(
            new ConfirmPasswordRecoveryCommand(
                CREDENTIAL,
                NEW_PASSWORD
            )
        );

        ArgumentCaptor<User> userCaptor =
            ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().passwordHash())
            .isEqualTo(NEW_PASSWORD_HASH);
        assertThat(userCaptor.getValue().updatedAt())
            .isEqualTo(NOW);

        verify(familyRepository).revokeAllActiveByUserId(
            USER_ID,
            NOW,
            RefreshTokenFamilyRevocationReason
                .PASSWORD_RECOVERY
        );
    }

    @Test
    void shouldHashBeforeCredentialConsumption() {
        when(passwordHashing.hash(NEW_PASSWORD))
            .thenReturn(NEW_PASSWORD_HASH);
        when(credentialConsumer.consume(
            CREDENTIAL,
            AccountActionCredentialPurpose
                .PASSWORD_RECOVERY
        ))
            .thenThrow(
                new InvalidAccountActionCredentialException()
            );

        assertThatThrownBy(() ->
            service.confirm(
                new ConfirmPasswordRecoveryCommand(
                    CREDENTIAL,
                    NEW_PASSWORD
                )
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            );

        verify(passwordHashing).hash(NEW_PASSWORD);
        verify(userRepository, never()).save(any());
        verify(familyRepository, never())
            .revokeAllActiveByUserId(
                any(),
                any(),
                any()
            );
    }

    @Test
    void shouldRejectUnavailableUser() {
        when(passwordHashing.hash(NEW_PASSWORD))
            .thenReturn(NEW_PASSWORD_HASH);
        when(credentialConsumer.consume(
            CREDENTIAL,
            AccountActionCredentialPurpose
                .PASSWORD_RECOVERY
        ))
            .thenReturn(USER_ID);
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(
                verifiedUser(UserStatus.CLOSED)
            ));

        assertThatThrownBy(() ->
            service.confirm(
                new ConfirmPasswordRecoveryCommand(
                    CREDENTIAL,
                    NEW_PASSWORD
                )
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            );

        verify(userRepository, never()).save(any());
        verify(familyRepository, never())
            .revokeAllActiveByUserId(
                any(),
                any(),
                any()
            );
    }

    @Test
    void shouldRejectUnverifiedUser() {
        when(passwordHashing.hash(NEW_PASSWORD))
            .thenReturn(NEW_PASSWORD_HASH);
        when(credentialConsumer.consume(
            CREDENTIAL,
            AccountActionCredentialPurpose
                .PASSWORD_RECOVERY
        ))
            .thenReturn(USER_ID);
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(unverifiedUser()));

        assertThatThrownBy(() ->
            service.confirm(
                new ConfirmPasswordRecoveryCommand(
                    CREDENTIAL,
                    NEW_PASSWORD
                )
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            );

        verify(userRepository, never()).save(any());
        verify(familyRepository, never())
            .revokeAllActiveByUserId(
                any(),
                any(),
                any()
            );
    }

    private static User verifiedUser(UserStatus status) {
        return User.rehydrate(
            USER_ID,
            EmailAddress.of("nursena@example.com"),
            "$2a$12$old-hashed-password",
            UserRole.USER,
            status,
            NOW.minusSeconds(30),
            NOW.minusSeconds(60),
            NOW.minusSeconds(30)
        );
    }

    private static User unverifiedUser() {
        return User.rehydrate(
            USER_ID,
            EmailAddress.of("nursena@example.com"),
            "$2a$12$old-hashed-password",
            UserRole.USER,
            UserStatus.ACTIVE,
            null,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60)
        );
    }
}
