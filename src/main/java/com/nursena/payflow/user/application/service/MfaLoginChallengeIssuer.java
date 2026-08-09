package com.nursena.payflow.user.application.service;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.MfaChallengeRequiredResult;
import com.nursena.payflow.user.application.port.out.GeneratedMfaLoginChallenge;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeGenerationPort;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeRepositoryPort;
import com.nursena.payflow.user.domain.model.MfaLoginChallenge;
import org.springframework.stereotype.Component;

@Component
class MfaLoginChallengeIssuer {

    private final MfaLoginChallengeGenerationPort generation;
    private final MfaLoginChallengeDigestPort digest;
    private final MfaLoginChallengeRepositoryPort repository;
    private final MfaLoginChallengeLifetimePolicy lifetimePolicy;

    MfaLoginChallengeIssuer(
        MfaLoginChallengeGenerationPort generation,
        MfaLoginChallengeDigestPort digest,
        MfaLoginChallengeRepositoryPort repository,
        MfaLoginChallengeLifetimePolicy lifetimePolicy
    ) {
        this.generation = generation;
        this.digest = digest;
        this.repository = repository;
        this.lifetimePolicy = lifetimePolicy;
    }

    MfaChallengeRequiredResult issue(UUID userId, Instant issuedAt) {
        repository.supersedePendingByUserId(userId, issuedAt);

        GeneratedMfaLoginChallenge generated = generation.generate();
        Instant expiresAt = lifetimePolicy.expiresAt(issuedAt);
        MfaLoginChallenge saved = repository.save(
            MfaLoginChallenge.issue(
                UUID.randomUUID(),
                userId,
                digest.digest(generated.value()),
                issuedAt,
                expiresAt,
                lifetimePolicy.maxAttempts()
            )
        );

        return new MfaChallengeRequiredResult(
            generated.value(),
            saved.expiresAt()
        );
    }
}
