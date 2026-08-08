package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentCommand;
import com.nursena.payflow.user.application.port.out.GeneratedTotpSecret;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.application.port.out.PasswordVerificationPort;
import com.nursena.payflow.user.application.port.out.TotpProvisioningUriPort;
import com.nursena.payflow.user.application.port.out.TotpSecretGenerationPort;
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
class BeginMfaEnrollmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

    @Mock UserRepositoryPort userRepository;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;
    @Mock PasswordVerificationPort passwordVerification;
    @Mock TotpSecretGenerationPort secretGeneration;
    @Mock MfaSecretProtectionPort secretProtection;
    @Mock TotpProvisioningUriPort provisioningUri;

    private User user;
    private BeginMfaEnrollmentService service;

    @BeforeEach
    void setUp() {
        user = User.register(EmailAddress.of("user@example.com"), "hash", NOW.minusSeconds(60));
        user.verifyEmail(NOW.minusSeconds(30));
        service = new BeginMfaEnrollmentService(
            userRepository,
            authenticatorRepository,
            passwordVerification,
            secretGeneration,
            secretProtection,
            provisioningUri,
            new MfaEnrollmentLifetimePolicy(Duration.ofMinutes(10)),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBeginEnrollmentAndReturnSecretOnce() {
        when(userRepository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(passwordVerification.matches("password", "hash")).thenReturn(true);
        when(authenticatorRepository.findByUserIdForUpdate(user.id())).thenReturn(Optional.empty());
        byte[] raw = "12345678901234567890".getBytes();
        when(secretGeneration.generate()).thenReturn(new GeneratedTotpSecret(raw, "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"));
        when(secretProtection.protect(user.id(), raw)).thenReturn(ProtectedMfaSecret.of(new byte[49]));
        when(provisioningUri.build(user.email(), "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP")).thenReturn("otpauth://totp/example");
        when(authenticatorRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.begin(new BeginMfaEnrollmentCommand(user.id(), "password"));

        assertThat(result.state()).isEqualTo(MfaLifecycleState.PENDING);
        assertThat(result.secret()).isEqualTo("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        verify(authenticatorRepository).save(any(MfaAuthenticator.class));
    }

    @Test
    void shouldRejectWrongCurrentPassword() {
        when(userRepository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(passwordVerification.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.begin(new BeginMfaEnrollmentCommand(user.id(), "wrong")))
            .isInstanceOf(MfaVerificationFailedException.class);
    }

    @Test
    void shouldRejectOverlappingEnrollment() {
        when(userRepository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(passwordVerification.matches("password", "hash")).thenReturn(true);
        when(authenticatorRepository.findByUserIdForUpdate(user.id()))
            .thenReturn(Optional.of(MfaAuthenticator.beginEnrollment(
                user.id(), ProtectedMfaSecret.of(new byte[49]), NOW, NOW.plusSeconds(600))));
        assertThatThrownBy(() -> service.begin(new BeginMfaEnrollmentCommand(user.id(), "password")))
            .isInstanceOf(MfaStateConflictException.class);
    }
}
