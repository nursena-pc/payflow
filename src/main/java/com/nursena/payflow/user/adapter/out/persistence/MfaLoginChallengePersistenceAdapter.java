package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.MfaLoginChallengeRepositoryPort;
import com.nursena.payflow.user.domain.model.MfaLoginChallenge;
import com.nursena.payflow.user.domain.model.MfaLoginChallengeDigest;
import org.springframework.stereotype.Component;

@Component
class MfaLoginChallengePersistenceAdapter
    implements MfaLoginChallengeRepositoryPort {

    private final SpringDataMfaLoginChallengeRepository repository;

    MfaLoginChallengePersistenceAdapter(
        SpringDataMfaLoginChallengeRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public MfaLoginChallenge save(MfaLoginChallenge challenge) {
        return toDomain(repository.saveAndFlush(toEntity(challenge)));
    }

    @Override
    public int supersedePendingByUserId(UUID userId, Instant resolvedAt) {
        return repository.supersedePendingByUserId(userId, resolvedAt);
    }

    @Override
    public Optional<UUID> findUserIdByDigest(
        MfaLoginChallengeDigest digest
    ) {
        return repository.findUserIdByDigest(digest.value());
    }

    @Override
    public Optional<MfaLoginChallenge> findByDigestForUpdate(
        MfaLoginChallengeDigest digest
    ) {
        return repository.findByDigestForUpdate(digest.value())
            .map(MfaLoginChallengePersistenceAdapter::toDomain);
    }

    private static MfaLoginChallengeJpaEntity toEntity(
        MfaLoginChallenge challenge
    ) {
        return new MfaLoginChallengeJpaEntity(
            challenge.id(),
            challenge.userId(),
            challenge.digest().value(),
            challenge.issuedAt(),
            challenge.expiresAt(),
            challenge.attemptsRemaining(),
            challenge.state(),
            challenge.resolvedAt()
        );
    }

    private static MfaLoginChallenge toDomain(
        MfaLoginChallengeJpaEntity entity
    ) {
        return MfaLoginChallenge.rehydrate(
            entity.getId(),
            entity.getUserId(),
            MfaLoginChallengeDigest.of(entity.getChallengeDigest()),
            entity.getIssuedAt(),
            entity.getExpiresAt(),
            entity.getAttemptsRemaining(),
            entity.getState(),
            entity.getResolvedAt()
        );
    }
}
