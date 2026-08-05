package com.nursena.payflow.user.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in
    .RequestPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
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
class RequestPasswordRecoveryServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "49f1f2f9-1a5d-4c95-95ad-1a4f514e65fd"
        );

    private static final Instant NOW =
        Instant.parse("2026-08-05T12:00:00Z");

    private static final EmailAddress EMAIL =
        EmailAddress.of("nursena@example.com");

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordRecoveryPreparationService
        preparationService;

    private RequestPasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new RequestPasswordRecoveryService(
            userRepository,
            preparationService
        );
    }

    @Test
    void shouldPrepareRecoveryForActiveVerifiedUser() {
        when(userRepository.findByEmailForUpdate(EMAIL))
            .thenReturn(Optional.of(verifiedUser(
                UserStatus.ACTIVE
            )));

        service.request(
            new RequestPasswordRecoveryCommand(
                "  Nursena@Example.COM  "
            )
        );

        verify(preparationService).prepare(USER_ID);
    }

    @Test
    void shouldReturnGenericallyForUnknownIdentity() {
        when(userRepository.findByEmailForUpdate(EMAIL))
            .thenReturn(Optional.empty());

        service.request(
            new RequestPasswordRecoveryCommand(
                EMAIL.value()
            )
        );

        verify(preparationService, never()).prepare(USER_ID);
    }

    @Test
    void shouldReturnGenericallyForUnverifiedIdentity() {
        when(userRepository.findByEmailForUpdate(EMAIL))
            .thenReturn(Optional.of(unverifiedUser()));

        service.request(
            new RequestPasswordRecoveryCommand(
                EMAIL.value()
            )
        );

        verify(preparationService, never()).prepare(USER_ID);
    }

    @Test
    void shouldReturnGenericallyForClosedIdentity() {
        when(userRepository.findByEmailForUpdate(EMAIL))
            .thenReturn(Optional.of(verifiedUser(
                UserStatus.CLOSED
            )));

        service.request(
            new RequestPasswordRecoveryCommand(
                EMAIL.value()
            )
        );

        verify(preparationService, never()).prepare(USER_ID);
    }

    private static User unverifiedUser() {
        return User.rehydrate(
            USER_ID,
            EMAIL,
            "$2a$12$hashed-password",
            UserRole.USER,
            UserStatus.ACTIVE,
            null,
            NOW,
            NOW
        );
    }

    private static User verifiedUser(UserStatus status) {
        return User.rehydrate(
            USER_ID,
            EMAIL,
            "$2a$12$hashed-password",
            UserRole.USER,
            status,
            NOW,
            NOW,
            NOW
        );
    }
}
