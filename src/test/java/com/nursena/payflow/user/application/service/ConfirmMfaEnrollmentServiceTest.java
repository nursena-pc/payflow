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
import java.util.List;
import java.util.Optional;

import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentCommand;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.application.port.out.TotpVerificationPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.MfaVerificationFailedException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import com.nursena.payflow.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfirmMfaEnrollmentServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-08-08T10:05:00Z");

    @Mock UserRepositoryPort userRepository;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;
    @Mock MfaSecretProtectionPort secretProtection;
    @Mock TotpVerificationPort totpVerification;
    @Mock MfaRecoveryCodeIssuer recoveryCodeIssuer;

    private User user;
    private MfaAuthenticator pending;
    private ConfirmMfaEnrollmentService service;

    @BeforeEach
    void setUp() {
        user = User.register(
            EmailAddress.of("user@example.com"),
            "hash",
            NOW.minusSeconds(600)
        );
        user.verifyEmail(NOW.minusSeconds(500));
        pending = MfaAuthenticator.beginEnrollment(
            user.id(),
            ProtectedMfaSecret.of(new byte[49]),
            NOW.minusSeconds(60),
            NOW.plusSeconds(540)
        );
        service = new ConfirmMfaEnrollmentService(
            userRepository,
            authenticatorRepository,
            secretProtection,
            totpVerification,
            recoveryCodeIssuer,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldActivateAndReturnRecoveryCodesAfterValidTotp() {
        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(pending));
        byte[] raw = "12345678901234567890".getBytes();
        when(secretProtection.reveal(
            user.id(),
            pending.protectedSecret()
        )).thenReturn(raw);
        when(totpVerification.verify(raw, "123456", NOW))
            .thenReturn(true);
        when(authenticatorRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        List<String> codes = List.of("Rc00000000000000000001");
        when(recoveryCodeIssuer.issue(user.id(), NOW))
            .thenReturn(codes);

        var result = service.confirm(
            new ConfirmMfaEnrollmentCommand(user.id(), "123456")
        );

        assertThat(result.state()).isEqualTo(MfaLifecycleState.ENABLED);
        assertThat(result.activatedAt()).isEqualTo(NOW);
        assertThat(result.recoveryCodes()).containsExactlyElementsOf(codes);
        verify(recoveryCodeIssuer).issue(user.id(), NOW);
    }

    @Test
    void shouldRejectInvalidTotpWithoutGeneratingRecoveryCodes() {
        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(pending));
        byte[] raw = "12345678901234567890".getBytes();
        when(secretProtection.reveal(
            user.id(),
            pending.protectedSecret()
        )).thenReturn(raw);
        when(totpVerification.verify(raw, "000000", NOW))
            .thenReturn(false);

        assertThatThrownBy(() -> service.confirm(
            new ConfirmMfaEnrollmentCommand(user.id(), "000000")
        )).isInstanceOf(MfaVerificationFailedException.class);

        verify(recoveryCodeIssuer, never()).issue(any(), any());
    }

    @Test
    void shouldRejectMissingPendingEnrollment() {
        when(userRepository.findByIdForUpdate(user.id()))
            .thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(
            new ConfirmMfaEnrollmentCommand(user.id(), "123456")
        )).isInstanceOf(MfaStateConflictException.class);
    }
}
