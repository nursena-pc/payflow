package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.IssueStepUpGrantResult;
import com.nursena.payflow.user.application.port.out.GeneratedStepUpGrant;
import com.nursena.payflow.user.application.port.out.StepUpGrantDigestPort;
import com.nursena.payflow.user.application.port.out.StepUpGrantGenerationPort;
import com.nursena.payflow.user.application.port.out.StepUpGrantRepositoryPort;
import com.nursena.payflow.user.domain.model.StepUpGrant;
import com.nursena.payflow.user.domain.model.StepUpGrantDigest;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepUpGrantIssuerTest {

    private static final UUID SUBJECT =
        UUID.fromString("10000000-0000-0000-0000-000000000102");
    private static final Instant NOW =
        Instant.parse("2026-08-10T10:00:00Z");
    private static final String TOKEN =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

    @Mock StepUpGrantGenerationPort generation;
    @Mock StepUpGrantDigestPort digest;
    @Mock StepUpGrantRepositoryPort repository;

    private StepUpGrantIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new StepUpGrantIssuer(
            generation,
            digest,
            repository,
            new StepUpGrantLifetimePolicy(Duration.ofMinutes(5))
        );
    }

    @Test
    void shouldSupersedePriorPurposeGrantBeforePersistingReplacement() {
        StepUpGrantDigest hashed = StepUpGrantDigest.of(new byte[32]);
        when(generation.generate()).thenReturn(new GeneratedStepUpGrant(TOKEN));
        when(digest.digest(TOKEN)).thenReturn(hashed);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IssueStepUpGrantResult result = issuer.issue(
            SUBJECT,
            StepUpPurpose.MFA_DISABLE,
            NOW
        );

        InOrder order = inOrder(repository, generation, digest);
        order.verify(repository).supersedeUnconsumedBySubjectAndPurpose(
            SUBJECT,
            StepUpPurpose.MFA_DISABLE,
            NOW
        );
        order.verify(generation).generate();
        order.verify(digest).digest(TOKEN);
        order.verify(repository).save(any(StepUpGrant.class));

        assertThat(result.grantToken()).isEqualTo(TOKEN);
        assertThat(result.purpose()).isEqualTo("mfa-disable");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(result.toString()).doesNotContain(TOKEN);
    }

    @Test
    void shouldPersistExactSubjectPurposeDigestAndLifetime() {
        StepUpGrantDigest hashed = StepUpGrantDigest.of(new byte[32]);
        when(generation.generate()).thenReturn(new GeneratedStepUpGrant(TOKEN));
        when(digest.digest(TOKEN)).thenReturn(hashed);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        issuer.issue(SUBJECT, StepUpPurpose.RECOVERY_CODE_ROTATION, NOW);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(grant ->
            grant.subjectId().equals(SUBJECT)
                && grant.purpose() == StepUpPurpose.RECOVERY_CODE_ROTATION
                && grant.digest().equals(hashed)
                && grant.issuedAt().equals(NOW)
                && grant.expiresAt().equals(NOW.plusSeconds(300))
        ));
    }
}
