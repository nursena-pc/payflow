package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import com.nursena.payflow.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelMfaEnrollmentServiceTest {

    @Mock UserRepositoryPort userRepository;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;

    @Test
    void shouldDeletePendingEnrollment() {
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        User user = User.register(EmailAddress.of("user@example.com"), "hash", now);
        MfaAuthenticator pending = MfaAuthenticator.beginEnrollment(
            user.id(), ProtectedMfaSecret.of(new byte[49]), now, now.plusSeconds(600));
        when(userRepository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id())).thenReturn(Optional.of(pending));
        new CancelMfaEnrollmentService(userRepository, authenticatorRepository).cancel(user.id());
        verify(authenticatorRepository).delete(pending);
    }

    @Test
    void shouldRejectCancellationWithoutPendingEnrollment() {
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        User user = User.register(EmailAddress.of("user@example.com"), "hash", now);
        when(userRepository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new CancelMfaEnrollmentService(userRepository, authenticatorRepository).cancel(user.id()))
            .isInstanceOf(MfaStateConflictException.class);
    }

    @Test
    void shouldRejectCancellationAfterActivation() {
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        User user = User.register(EmailAddress.of("user@example.com"), "hash", now.minusSeconds(60));
        MfaAuthenticator enabled = MfaAuthenticator.beginEnrollment(
            user.id(), ProtectedMfaSecret.of(new byte[49]), now.minusSeconds(30), now.plusSeconds(570))
            .activate(now);
        when(userRepository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(user.id())).thenReturn(Optional.of(enabled));
        assertThatThrownBy(() -> new CancelMfaEnrollmentService(userRepository, authenticatorRepository).cancel(user.id()))
            .isInstanceOf(MfaStateConflictException.class);
    }
}
