package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.DisableMfaCommand;
import com.nursena.payflow.user.application.port.in.StepUpAuthorizationPolicy;
import com.nursena.payflow.user.application.port.out.AccountSecurityAuditPort;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.AccountSecurityAuditAction;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import com.nursena.payflow.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisableMfaServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-08-11T10:00:00Z");
    private static final String STEP_UP_GRANT = "step-up-grant";

    @Mock UserRepositoryPort userRepository;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;
    @Mock MfaRecoveryCodeRepositoryPort recoveryCodeRepository;
    @Mock StepUpAuthorizationPolicy stepUpAuthorizationPolicy;
    @Mock RefreshTokenFamilyRepositoryPort refreshTokenFamilyRepository;
    @Mock AccountSecurityAuditPort auditPort;

    private DisableMfaService service;

    @BeforeEach
    void setUp() {
        service = new DisableMfaService(
            userRepository,
            authenticatorRepository,
            recoveryCodeRepository,
            stepUpAuthorizationPolicy,
            refreshTokenFamilyRepository,
            auditPort,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldDisableActiveMfaAndRevokeExistingSessions() {
        User user = activeUser();
        MfaAuthenticator authenticator = activeAuthenticator(user.id());

        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(authenticator));

        service.disable(new DisableMfaCommand(user.id(), STEP_UP_GRANT));

        InOrder ordered = inOrder(
            userRepository,
            authenticatorRepository,
            stepUpAuthorizationPolicy,
            recoveryCodeRepository,
            refreshTokenFamilyRepository,
            auditPort
        );

        ordered.verify(userRepository).findByIdForUpdate(user.id());
        ordered.verify(authenticatorRepository)
            .findByUserIdForUpdate(user.id());
        ordered.verify(stepUpAuthorizationPolicy).requireAndConsume(
            user.id(),
            StepUpPurpose.MFA_DISABLE,
            STEP_UP_GRANT
        );
        ordered.verify(recoveryCodeRepository)
            .deleteAllByUserId(user.id());
        ordered.verify(authenticatorRepository).delete(authenticator);
        ordered.verify(refreshTokenFamilyRepository)
            .revokeAllActiveByUserId(
                user.id(),
                NOW,
                RefreshTokenFamilyRevocationReason.MFA_DISABLED
            );
        ordered.verify(auditPort).append(argThat(event ->
            event.id() != null
                && event.subjectUserId().equals(user.id())
                && event.action()
                    == AccountSecurityAuditAction.MFA_DISABLED
                && event.occurredAt().equals(NOW)
        ));
    }

    @Test
    void shouldRejectUnknownUserBeforeInspectingMfaState() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findByIdForUpdate(userId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.disable(new DisableMfaCommand(userId, STEP_UP_GRANT))
        ).isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(
            authenticatorRepository,
            recoveryCodeRepository,
            stepUpAuthorizationPolicy,
            refreshTokenFamilyRepository,
            auditPort
        );
    }

    @Test
    void shouldRejectWhenAuthenticatorDoesNotExist() {
        User user = activeUser();

        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.disable(
                new DisableMfaCommand(user.id(), STEP_UP_GRANT)
            )
        ).isInstanceOf(MfaStateConflictException.class);

        verifyNoInteractions(
            recoveryCodeRepository,
            stepUpAuthorizationPolicy,
            refreshTokenFamilyRepository,
            auditPort
        );
    }

    @Test
    void shouldRejectWhenAuthenticatorIsNotActive() {
        User user = activeUser();
        MfaAuthenticator pending = pendingAuthenticator(user.id());

        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(pending));

        assertThatThrownBy(() ->
            service.disable(
                new DisableMfaCommand(user.id(), STEP_UP_GRANT)
            )
        ).isInstanceOf(MfaStateConflictException.class);

        verify(authenticatorRepository, never()).delete(any());
        verifyNoInteractions(
            recoveryCodeRepository,
            stepUpAuthorizationPolicy,
            refreshTokenFamilyRepository,
            auditPort
        );
    }

    @Test
    void shouldNotMutateMfaStateWhenStepUpGrantIsRejected() {
        User user = activeUser();
        MfaAuthenticator authenticator = activeAuthenticator(user.id());

        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(authenticator));

        doThrow(new InvalidStepUpGrantException())
            .when(stepUpAuthorizationPolicy)
            .requireAndConsume(
                user.id(),
                StepUpPurpose.MFA_DISABLE,
                STEP_UP_GRANT
            );

        assertThatThrownBy(() ->
            service.disable(
                new DisableMfaCommand(user.id(), STEP_UP_GRANT)
            )
        ).isInstanceOf(InvalidStepUpGrantException.class);

        verify(authenticatorRepository, never()).delete(any());
        verifyNoInteractions(
            recoveryCodeRepository,
            refreshTokenFamilyRepository,
            auditPort
        );
    }

    private static User activeUser() {
        return User.register(
            EmailAddress.of("user@example.com"),
            "password-hash",
            NOW.minusSeconds(60)
        );
    }

    private static MfaAuthenticator pendingAuthenticator(UUID userId) {
        return MfaAuthenticator.beginEnrollment(
            userId,
            ProtectedMfaSecret.of(new byte[49]),
            NOW.minusSeconds(30),
            NOW.plusSeconds(570)
        );
    }

    private static MfaAuthenticator activeAuthenticator(UUID userId) {
        return pendingAuthenticator(userId).activate(NOW);
    }
}
