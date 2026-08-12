package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesCommand;
import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesResult;
import com.nursena.payflow.user.application.port.in.StepUpAuthorizationPolicy;
import com.nursena.payflow.user.application.port.out.AccountSecurityAuditPort;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.AccountSecurityAuditAction;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import com.nursena.payflow.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotateMfaRecoveryCodesServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-08-11T11:00:00Z");
    private static final String STEP_UP_GRANT = "step-up-grant";

    @Mock UserRepositoryPort userRepository;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;
    @Mock MfaRecoveryCodeRepositoryPort recoveryCodeRepository;
    @Mock MfaRecoveryCodeIssuer recoveryCodeIssuer;
    @Mock StepUpAuthorizationPolicy stepUpAuthorizationPolicy;
    @Mock AccountSecurityAuditPort auditPort;

    private RotateMfaRecoveryCodesService service;

    @BeforeEach
    void setUp() {
        service = new RotateMfaRecoveryCodesService(
            userRepository,
            authenticatorRepository,
            recoveryCodeRepository,
            recoveryCodeIssuer,
            stepUpAuthorizationPolicy,
            auditPort,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldRotateRecoveryCodesAfterLockingMfaState() {
        User user = activeUser();
        MfaAuthenticator authenticator =
            activeAuthenticator(user.id());
        List<String> issuedCodes = new ArrayList<>(
            List.of("code-1", "code-2")
        );

        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(authenticator));
        when(recoveryCodeIssuer.issue(user.id(), NOW))
            .thenReturn(issuedCodes);

        RotateMfaRecoveryCodesResult result = service.rotate(
            new RotateMfaRecoveryCodesCommand(
                user.id(),
                STEP_UP_GRANT
            )
        );

        assertThat(result.recoveryCodes())
            .containsExactly("code-1", "code-2");

        issuedCodes.add("late-mutation");

        assertThat(result.recoveryCodes())
            .containsExactly("code-1", "code-2");
        assertThatThrownBy(() ->
            result.recoveryCodes().add("forbidden")
        ).isInstanceOf(UnsupportedOperationException.class);

        InOrder ordered = inOrder(
            userRepository,
            authenticatorRepository,
            stepUpAuthorizationPolicy,
            recoveryCodeRepository,
            recoveryCodeIssuer,
            auditPort
        );

        ordered.verify(userRepository)
            .findByIdForUpdate(user.id());
        ordered.verify(authenticatorRepository)
            .findByUserIdForUpdate(user.id());
        ordered.verify(stepUpAuthorizationPolicy)
            .requireAndConsume(
                user.id(),
                StepUpPurpose.RECOVERY_CODE_ROTATION,
                STEP_UP_GRANT
            );
        ordered.verify(recoveryCodeRepository)
            .deleteAllByUserId(user.id());
        ordered.verify(recoveryCodeIssuer)
            .issue(user.id(), NOW);
        ordered.verify(auditPort).append(argThat(event ->
            event.id() != null
                && event.subjectUserId().equals(user.id())
                && event.action()
                    == AccountSecurityAuditAction.RECOVERY_CODES_ROTATED
                && event.occurredAt().equals(NOW)
        ));
    }

    @Test
    void shouldRejectUnknownUserBeforeInspectingMfaState() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findByIdForUpdate(userId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(
            new RotateMfaRecoveryCodesCommand(
                userId,
                STEP_UP_GRANT
            )
        )).isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(
            authenticatorRepository,
            recoveryCodeRepository,
            recoveryCodeIssuer,
            stepUpAuthorizationPolicy,
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

        assertThatThrownBy(() -> service.rotate(
            new RotateMfaRecoveryCodesCommand(
                user.id(),
                STEP_UP_GRANT
            )
        )).isInstanceOf(MfaStateConflictException.class);

        verifyNoInteractions(
            recoveryCodeRepository,
            recoveryCodeIssuer,
            stepUpAuthorizationPolicy,
            auditPort
        );
    }

    @Test
    void shouldRejectWhenAuthenticatorIsNotActive() {
        User user = activeUser();
        MfaAuthenticator pending =
            pendingAuthenticator(user.id());

        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.rotate(
            new RotateMfaRecoveryCodesCommand(
                user.id(),
                STEP_UP_GRANT
            )
        )).isInstanceOf(MfaStateConflictException.class);

        verify(recoveryCodeRepository, never())
            .deleteAllByUserId(user.id());
        verifyNoInteractions(
            recoveryCodeIssuer,
            stepUpAuthorizationPolicy,
            auditPort
        );
    }

    @Test
    void shouldNotReplaceCodesWhenStepUpGrantIsRejected() {
        User user = activeUser();
        MfaAuthenticator authenticator =
            activeAuthenticator(user.id());

        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(authenticator));

        doThrow(new InvalidStepUpGrantException())
            .when(stepUpAuthorizationPolicy)
            .requireAndConsume(
                user.id(),
                StepUpPurpose.RECOVERY_CODE_ROTATION,
                STEP_UP_GRANT
            );

        assertThatThrownBy(() -> service.rotate(
            new RotateMfaRecoveryCodesCommand(
                user.id(),
                STEP_UP_GRANT
            )
        )).isInstanceOf(InvalidStepUpGrantException.class);

        verify(recoveryCodeRepository, never())
            .deleteAllByUserId(user.id());
        verifyNoInteractions(recoveryCodeIssuer, auditPort);
    }

    @Test
    void shouldPropagateAuditFailureForTransactionRollback() {
        User user = activeUser();
        MfaAuthenticator authenticator =
            activeAuthenticator(user.id());
        RuntimeException auditFailure =
            new RuntimeException("audit unavailable");

        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(authenticator));
        when(recoveryCodeIssuer.issue(user.id(), NOW))
            .thenReturn(List.of("code-1", "code-2"));

        doThrow(auditFailure)
            .when(auditPort)
            .append(argThat(event ->
                event.action()
                    == AccountSecurityAuditAction.RECOVERY_CODES_ROTATED
            ));

        assertThatThrownBy(() -> service.rotate(
            new RotateMfaRecoveryCodesCommand(
                user.id(),
                STEP_UP_GRANT
            )
        )).isSameAs(auditFailure);

        verify(recoveryCodeRepository)
            .deleteAllByUserId(user.id());
        verify(recoveryCodeIssuer).issue(user.id(), NOW);
    }

    private static User activeUser() {
        return User.register(
            EmailAddress.of("user@example.com"),
            "password-hash",
            NOW.minusSeconds(60)
        );
    }

    private static MfaAuthenticator pendingAuthenticator(
        UUID userId
    ) {
        return MfaAuthenticator.beginEnrollment(
            userId,
            ProtectedMfaSecret.of(new byte[49]),
            NOW.minusSeconds(30),
            NOW.plusSeconds(570)
        );
    }

    private static MfaAuthenticator activeAuthenticator(
        UUID userId
    ) {
        return pendingAuthenticator(userId).activate(NOW);
    }
}