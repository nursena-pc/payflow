package com.nursena.payflow.user.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in
    .RequestEmailVerificationCommand;
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
class RequestEmailVerificationServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "33ed1a90-11ec-48b3-b38b-beb7cda6bc90"
        );

    private static final Instant NOW =
        Instant.parse("2026-08-05T12:00:00Z");

    private static final EmailAddress EMAIL =
        EmailAddress.of("nursena@example.com");

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private EmailVerificationPreparationService
        preparationService;

    private RequestEmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new RequestEmailVerificationService(
            userRepository,
            preparationService
        );
    }

    @Test
    void shouldPrepareVerificationForActiveUnverifiedUser() {
        when(userRepository.findByEmailForUpdate(EMAIL))
            .thenReturn(Optional.of(unverifiedUser()));

        service.request(
            new RequestEmailVerificationCommand(
                "  Nursena@Example.COM  "
            )
        );

        verify(preparationService).prepare(USER_ID, EMAIL);
    }

    @Test
    void shouldReturnGenericallyForUnknownIdentity() {
        when(userRepository.findByEmailForUpdate(EMAIL))
            .thenReturn(Optional.empty());

        service.request(
            new RequestEmailVerificationCommand(
                EMAIL.value()
            )
        );

        verify(preparationService, never()).prepare(USER_ID, EMAIL);
    }

    @Test
    void shouldReturnGenericallyForVerifiedIdentity() {
        when(userRepository.findByEmailForUpdate(EMAIL))
            .thenReturn(Optional.of(verifiedUser()));

        service.request(
            new RequestEmailVerificationCommand(
                EMAIL.value()
            )
        );

        verify(preparationService, never()).prepare(USER_ID, EMAIL);
    }

    @Test
    void shouldReturnGenericallyForClosedIdentity() {
        when(userRepository.findByEmailForUpdate(EMAIL))
            .thenReturn(Optional.of(closedUser()));

        service.request(
            new RequestEmailVerificationCommand(
                EMAIL.value()
            )
        );

        verify(preparationService, never()).prepare(USER_ID, EMAIL);
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

    private static User verifiedUser() {
        return User.rehydrate(
            USER_ID,
            EMAIL,
            "$2a$12$hashed-password",
            UserRole.USER,
            UserStatus.ACTIVE,
            NOW,
            NOW,
            NOW
        );
    }

    private static User closedUser() {
        return User.rehydrate(
            USER_ID,
            EMAIL,
            "$2a$12$hashed-password",
            UserRole.USER,
            UserStatus.CLOSED,
            null,
            NOW,
            NOW
        );
    }
}
