package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.StepUpGrantDigestPort;
import com.nursena.payflow.user.application.port.out.StepUpGrantRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.model.StepUpGrant;
import com.nursena.payflow.user.domain.model.StepUpGrantDigest;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersistentStepUpAuthorizationPolicyTest {

    private static final UUID SUBJECT =
        UUID.fromString("10000000-0000-0000-0000-000000000104");
    private static final Instant NOW =
        Instant.parse("2026-08-10T10:00:00Z");
    private static final StepUpGrantDigest DIGEST =
        StepUpGrantDigest.of(new byte[32]);

    @Mock StepUpGrantDigestPort digestPort;
    @Mock StepUpGrantRepositoryPort repository;

    private PersistentStepUpAuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PersistentStepUpAuthorizationPolicy(
            digestPort,
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldConsumeMatchingSubjectPurposeGrantExactlyOnce() {
        StepUpGrant grant = grant(SUBJECT, StepUpPurpose.MFA_DISABLE, NOW.plusSeconds(60), null, null);
        when(digestPort.digest("grant")).thenReturn(DIGEST);
        when(repository.findByDigestForUpdate(DIGEST)).thenReturn(Optional.of(grant));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        policy.requireAndConsume(SUBJECT, StepUpPurpose.MFA_DISABLE, "grant");

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(value ->
            NOW.equals(value.consumedAt())
        ));
    }

    @Test
    void shouldRejectWrongSubjectOrPurposeThroughSameContract() {
        when(digestPort.digest("grant")).thenReturn(DIGEST);
        when(repository.findByDigestForUpdate(DIGEST))
            .thenReturn(Optional.of(grant(SUBJECT, StepUpPurpose.MFA_DISABLE, NOW.plusSeconds(60), null, null)));

        assertThatThrownBy(() -> policy.requireAndConsume(
            UUID.randomUUID(), StepUpPurpose.MFA_DISABLE, "grant"
        )).isInstanceOf(InvalidStepUpGrantException.class);
        assertThatThrownBy(() -> policy.requireAndConsume(
            SUBJECT, StepUpPurpose.RECOVERY_CODE_ROTATION, "grant"
        )).isInstanceOf(InvalidStepUpGrantException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectExpiredSupersededOrReplayedGrantThroughSameContract() {
        when(digestPort.digest("grant")).thenReturn(DIGEST);
        StepUpGrant expired = grant(SUBJECT, StepUpPurpose.MFA_DISABLE, NOW, null, null);
        when(repository.findByDigestForUpdate(DIGEST)).thenReturn(Optional.of(expired));
        assertInvalid();

        StepUpGrant superseded = grant(SUBJECT, StepUpPurpose.MFA_DISABLE, NOW.plusSeconds(60), null, NOW.minusSeconds(1));
        when(repository.findByDigestForUpdate(DIGEST)).thenReturn(Optional.of(superseded));
        assertInvalid();

        StepUpGrant replayed = grant(SUBJECT, StepUpPurpose.MFA_DISABLE, NOW.plusSeconds(60), NOW.minusSeconds(1), null);
        when(repository.findByDigestForUpdate(DIGEST)).thenReturn(Optional.of(replayed));
        assertInvalid();
    }

    @Test
    void shouldRejectUnknownMalformedOrOversizedCredentialThroughSameContract() {
        when(digestPort.digest("unknown")).thenReturn(DIGEST);
        when(repository.findByDigestForUpdate(DIGEST)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> policy.requireAndConsume(
            SUBJECT, StepUpPurpose.MFA_DISABLE, "unknown"
        )).isInstanceOf(InvalidStepUpGrantException.class);
        assertThatThrownBy(() -> policy.requireAndConsume(
            SUBJECT, StepUpPurpose.MFA_DISABLE, ""
        )).isInstanceOf(InvalidStepUpGrantException.class);
        assertThatThrownBy(() -> policy.requireAndConsume(
            SUBJECT, StepUpPurpose.MFA_DISABLE, "x".repeat(257)
        )).isInstanceOf(InvalidStepUpGrantException.class);
    }

    private void assertInvalid() {
        assertThatThrownBy(() -> policy.requireAndConsume(
            SUBJECT, StepUpPurpose.MFA_DISABLE, "grant"
        )).isInstanceOf(InvalidStepUpGrantException.class);
    }

    private static StepUpGrant grant(
        UUID subject,
        StepUpPurpose purpose,
        Instant expires,
        Instant consumed,
        Instant superseded
    ) {
        return StepUpGrant.rehydrate(
            UUID.randomUUID(), subject, purpose, DIGEST,
            NOW.minusSeconds(60), expires, consumed, superseded
        );
    }
}
