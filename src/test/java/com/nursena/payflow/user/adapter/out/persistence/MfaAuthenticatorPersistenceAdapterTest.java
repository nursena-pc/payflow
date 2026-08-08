package com.nursena.payflow.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MfaAuthenticatorPersistenceAdapterTest {

    @Mock SpringDataMfaAuthenticatorRepository repository;
    private MfaAuthenticatorPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MfaAuthenticatorPersistenceAdapter(repository);
    }

    @Test
    void shouldSaveAndRestorePendingAuthenticator() {
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        MfaAuthenticator pending = MfaAuthenticator.beginEnrollment(UUID.randomUUID(), ProtectedMfaSecret.of(new byte[49]), now, now.plusSeconds(600));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MfaAuthenticator saved = adapter.save(pending);
        assertThat(saved.userId()).isEqualTo(pending.userId());
        assertThat(saved.protectedSecret().value()).isEqualTo(pending.protectedSecret().value());
    }

    @Test
    void shouldUsePessimisticLookupForUpdate() {
        UUID userId = UUID.randomUUID();
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());
        assertThat(adapter.findByUserIdForUpdate(userId)).isEmpty();
        verify(repository).findByUserIdForUpdate(userId);
    }

    @Test
    void shouldDeleteAuthenticatorByUserId() {
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        MfaAuthenticator pending = MfaAuthenticator.beginEnrollment(UUID.randomUUID(), ProtectedMfaSecret.of(new byte[49]), now, now.plusSeconds(600));
        adapter.delete(pending);
        verify(repository).deleteById(pending.userId());
        verify(repository).flush();
    }
}
