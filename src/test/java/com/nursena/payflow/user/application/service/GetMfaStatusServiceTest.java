package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import com.nursena.payflow.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMfaStatusServiceTest {

    @Mock UserRepositoryPort userRepository;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;

    @Test
    void shouldReportDisabledWhenAuthenticatorDoesNotExist() {
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        User user = User.register(EmailAddress.of("user@example.com"), "hash", now);
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserId(user.id())).thenReturn(Optional.empty());
        var result = new GetMfaStatusService(userRepository, authenticatorRepository).getStatus(user.id());
        assertThat(result.state()).isEqualTo(MfaLifecycleState.DISABLED);
    }

    @Test
    void shouldReportPendingMetadataWithoutSecret() {
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        User user = User.register(EmailAddress.of("user@example.com"), "hash", now.minusSeconds(60));
        MfaAuthenticator pending = MfaAuthenticator.beginEnrollment(user.id(), ProtectedMfaSecret.of(new byte[49]), now, now.plusSeconds(600));
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserId(user.id())).thenReturn(Optional.of(pending));
        var result = new GetMfaStatusService(userRepository, authenticatorRepository).getStatus(user.id());
        assertThat(result.state()).isEqualTo(MfaLifecycleState.PENDING);
        assertThat(result.enrollmentExpiresAt()).isEqualTo(now.plusSeconds(600));
    }
}
