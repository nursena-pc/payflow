package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.StepUpGrantRepositoryPort;
import com.nursena.payflow.user.domain.model.StepUpGrant;
import com.nursena.payflow.user.domain.model.StepUpGrantDigest;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import org.springframework.stereotype.Component;

@Component
class StepUpGrantPersistenceAdapter implements StepUpGrantRepositoryPort {

    private final SpringDataStepUpGrantRepository repository;

    StepUpGrantPersistenceAdapter(SpringDataStepUpGrantRepository repository) {
        this.repository = repository;
    }

    @Override
    public StepUpGrant save(StepUpGrant grant) {
        return toDomain(repository.saveAndFlush(toEntity(grant)));
    }

    @Override
    public Optional<StepUpGrant> findByDigestForUpdate(
        StepUpGrantDigest digest
    ) {
        return repository.findByDigestForUpdate(digest.value())
            .map(StepUpGrantPersistenceAdapter::toDomain);
    }

    @Override
    public int supersedeUnconsumedBySubjectAndPurpose(
        UUID subjectId,
        StepUpPurpose purpose,
        Instant supersededAt
    ) {
        return repository.supersedeUnconsumedBySubjectAndPurpose(
            subjectId,
            purpose.value(),
            supersededAt
        );
    }

    private static StepUpGrantJpaEntity toEntity(StepUpGrant grant) {
        return new StepUpGrantJpaEntity(
            grant.id(),
            grant.subjectId(),
            grant.purpose().value(),
            grant.digest().value(),
            grant.issuedAt(),
            grant.expiresAt(),
            grant.consumedAt(),
            grant.supersededAt()
        );
    }

    private static StepUpGrant toDomain(StepUpGrantJpaEntity entity) {
        return StepUpGrant.rehydrate(
            entity.getId(),
            entity.getSubjectId(),
            StepUpPurpose.fromValue(entity.getPurpose()),
            StepUpGrantDigest.of(entity.getGrantDigest()),
            entity.getIssuedAt(),
            entity.getExpiresAt(),
            entity.getConsumedAt(),
            entity.getSupersededAt()
        );
    }
}
