package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

    private static final Instant NOW = Instant.parse("2026-08-08T10:05:00Z");
    @Mock UserRepositoryPort userRepository;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;
    @Mock MfaSecretProtectionPort secretProtection;
    @Mock TotpVerificationPort totpVerification;
    private User user;
    private MfaAuthenticator pending;
    private ConfirmMfaEnrollmentService service;

    @BeforeEach
    void setUp() {
        user = User.register(EmailAddress.of("user@example.com"), "hash", NOW.minusSeconds(600));
        user.verifyEmail(NOW.minusSeconds(500));
        pending = MfaAuthenticator.beginEnrollment(user.id(), ProtectedMfaSecret.of(new byte[49]), NOW.minusSeconds(60), NOW.plusSeconds(540));
        service = new ConfirmMfaEnrollmentService(userRepository, authenticatorRepository, secretProtection, totpVerification, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldActivateAfterValidTotp() {
        when(userRepository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id())).thenReturn(Optional.of(pending));
        byte[] raw = "12345678901234567890".getBytes();
        when(secretProtection.reveal(user.id(), pending.protectedSecret())).thenReturn(raw);
        when(totpVerification.verify(raw, "123456", NOW)).thenReturn(true);
        when(authenticatorRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.confirm(new ConfirmMfaEnrollmentCommand(user.id(), "123456"));
        assertThat(result.state()).isEqualTo(MfaLifecycleState.ENABLED);
        assertThat(result.activatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectInvalidTotpWithoutActivating() {
        when(userRepository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id())).thenReturn(Optional.of(pending));
        byte[] raw = "12345678901234567890".getBytes();
        when(secretProtection.reveal(user.id(), pending.protectedSecret())).thenReturn(raw);
        when(totpVerification.verify(raw, "000000", NOW)).thenReturn(false);
        assertThatThrownBy(() -> service.confirm(new ConfirmMfaEnrollmentCommand(user.id(), "000000")))
            .isInstanceOf(MfaVerificationFailedException.class);
    }

    @Test
    void shouldRejectMissingPendingEnrollment() {
        when(userRepository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm(new ConfirmMfaEnrollmentCommand(user.id(), "123456")))
            .isInstanceOf(MfaStateConflictException.class);
    }
}
