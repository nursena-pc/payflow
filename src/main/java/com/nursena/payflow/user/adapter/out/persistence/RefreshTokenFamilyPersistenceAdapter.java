package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenFamilyPersistenceAdapter
    implements RefreshTokenFamilyRepositoryPort {

    private final SpringDataRefreshTokenFamilyRepository
        repository;

    RefreshTokenFamilyPersistenceAdapter(
        SpringDataRefreshTokenFamilyRepository
            repository
    ) {
        this.repository = repository;
    }

    @Override
    public RefreshTokenFamily save(
        RefreshTokenFamily family
    ) {
        RefreshTokenFamilyJpaEntity saved =
            repository.saveAndFlush(
                RefreshTokenPersistenceMapper
                    .toFamilyEntity(family)
            );

        return RefreshTokenPersistenceMapper
            .toFamilyDomain(saved);
    }

    @Override
    public Optional<RefreshTokenFamily>
    findByIdForUpdate(
        RefreshTokenFamilyId familyId
    ) {
        return repository
            .findByIdForUpdate(
                familyId.value()
            )
            .map(
                RefreshTokenPersistenceMapper
                    ::toFamilyDomain
            );
    }

    @Override
    public int revokeAllActiveByUserId(
        UUID userId,
        Instant revokedAt,
        RefreshTokenFamilyRevocationReason reason
    ) {
        return repository
            .revokeAllActiveByUserId(
                userId,
                revokedAt,
                reason
            );
    }
}
