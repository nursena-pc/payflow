package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.MfaChallengeRequiredResult;
import com.nursena.payflow.user.application.port.out.GeneratedMfaLoginChallenge;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeGenerationPort;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeRepositoryPort;
import com.nursena.payflow.user.domain.model.MfaLoginChallenge;
import com.nursena.payflow.user.domain.model.MfaLoginChallengeDigest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MfaLoginChallengeIssuerTest {

    private static final UUID USER_ID = UUID.fromString("22e374a7-604d-4bcc-8d42-73a84fe58a6a");
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

    @Mock MfaLoginChallengeGenerationPort generation;
    @Mock MfaLoginChallengeDigestPort digest;
    @Mock MfaLoginChallengeRepositoryPort repository;

    private MfaLoginChallengeIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new MfaLoginChallengeIssuer(
            generation,
            digest,
            repository,
            new MfaLoginChallengeLifetimePolicy(Duration.ofMinutes(5), 5)
        );
    }

    @Test
    void shouldSupersedePriorPendingChallengeBeforeIssuingNewOne() {
        when(generation.generate()).thenReturn(new GeneratedMfaLoginChallenge("challenge"));
        when(digest.digest("challenge")).thenReturn(MfaLoginChallengeDigest.of(new byte[32]));
        when(repository.save(any(MfaLoginChallenge.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        issuer.issue(USER_ID, NOW);

        InOrder order = inOrder(repository, generation);
        order.verify(repository).supersedePendingByUserId(USER_ID, NOW);
        order.verify(generation).generate();
        order.verify(repository).save(any(MfaLoginChallenge.class));
    }

    @Test
    void shouldReturnPlaintextChallengeOnlyFromIssuerBoundary() {
        when(generation.generate()).thenReturn(new GeneratedMfaLoginChallenge("challenge"));
        when(digest.digest("challenge")).thenReturn(MfaLoginChallengeDigest.of(new byte[32]));
        when(repository.save(any(MfaLoginChallenge.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        MfaChallengeRequiredResult result = issuer.issue(USER_ID, NOW);

        assertThat(result.challengeToken()).isEqualTo("challenge");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void shouldPersistDigestAndAttemptBudget() {
        MfaLoginChallengeDigest expectedDigest = MfaLoginChallengeDigest.of(new byte[32]);
        when(generation.generate()).thenReturn(new GeneratedMfaLoginChallenge("challenge"));
        when(digest.digest("challenge")).thenReturn(expectedDigest);
        when(repository.save(any(MfaLoginChallenge.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        issuer.issue(USER_ID, NOW);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(challenge ->
            challenge.userId().equals(USER_ID)
                && challenge.digest().equals(expectedDigest)
                && challenge.attemptsRemaining() == 5
                && challenge.expiresAt().equals(NOW.plusSeconds(300))
        ));
    }
}
