package com.nursena.payflow.user.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenRecordPersistenceAdapter
    implements RefreshTokenRecordRepositoryPort {

    private final SpringDataRefreshTokenRecordRepository
        recordRepository;

    private final SpringDataRefreshTokenFamilyRepository
        familyRepository;

    RefreshTokenRecordPersistenceAdapter(
        SpringDataRefreshTokenRecordRepository
            recordRepository,
        SpringDataRefreshTokenFamilyRepository
            familyRepository
    ) {
        this.recordRepository = recordRepository;
        this.familyRepository = familyRepository;
    }

    @Override
    public RefreshTokenRecord save(
        RefreshTokenRecord record
    ) {
        RefreshTokenRecordJpaEntity saved =
            recordRepository.saveAndFlush(
                RefreshTokenPersistenceMapper
                    .toRecordEntity(record)
            );

        return toDomain(saved);
    }

    @Override
    public Optional<RefreshTokenRecord>
    findByDigestForUpdate(
        RefreshTokenDigest digest
    ) {
        return recordRepository
            .findByDigestForUpdate(
                digest.value()
            )
            .map(this::toDomain);
    }

    @Override
    public Optional<RefreshTokenRecord> findById(
        RefreshTokenRecordId recordId
    ) {
        return recordRepository
            .findById(
                recordId.value()
            )
            .map(this::toDomain);
    }

    private RefreshTokenRecord toDomain(
        RefreshTokenRecordJpaEntity entity
    ) {
        RefreshTokenFamily family =
            familyRepository
                .findById(
                    entity.getFamilyId()
                )
                .map(
                    RefreshTokenPersistenceMapper
                        ::toFamilyDomain
                )
                .orElseThrow(
                    () -> missingFamily(entity)
                );

        return RefreshTokenPersistenceMapper
            .toRecordDomain(
                entity,
                family
            );
    }

    private static IllegalStateException
    missingFamily(
        RefreshTokenRecordJpaEntity entity
    ) {
        UUID familyId =
            entity.getFamilyId();

        UUID recordId =
            entity.getId();

        return new IllegalStateException(
            "refresh token family "
                + familyId
                + " was not found for record "
                + recordId
        );
    }
}
